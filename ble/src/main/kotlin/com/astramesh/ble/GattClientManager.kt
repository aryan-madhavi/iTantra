package com.astramesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.common.AstraResult
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages Central GATT Client interactions: connection, MTU negotiation, PHY, notifications, and paced writing frames.
 * Hardened to prevent Android Bluetooth stack L2CAP buffer saturation and native handle exhaustion.
 */
@SuppressLint("MissingPermission")
class GattClientManager(
    private val context: Context,
    private val connectionPool: BleConnectionPool
) {
    private val _incomingSlices = MutableSharedFlow<Pair<NodeId, BleSlice>>(extraBufferCapacity = 128)
    val incomingSlices: SharedFlow<Pair<NodeId, BleSlice>> = _incomingSlices.asSharedFlow()

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun connectPeer(nodeId: NodeId, deviceAddress: String): AstraResult<Unit> {
        val adapter = bluetoothAdapter ?: return AstraResult.Failure("Bluetooth adapter not available")
        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            return AstraResult.Failure("Invalid device address: $deviceAddress", e)
        }

        // Close existing handle if re-connecting
        val existingHandle = connectionPool.get(nodeId)
        if (existingHandle != null) {
            try {
                existingHandle.bluetoothGatt?.close()
            } catch (_: Exception) {}
            connectionPool.removeConnection(nodeId)
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    AstraLog.i("GattClientManager", "Connected to GATT peer ${device.address}")
                    // Negotiate high MTU
                    gatt.requestMtu(AstraNetworkConfig.REQUESTED_BLE_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    AstraLog.w("GattClientManager", "Disconnected from GATT peer ${device.address}")
                    connectionPool.removeConnection(nodeId)
                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        AstraLog.w("GattClientManager", "Error closing gatt on disconnect: ${e.message}")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                AstraLog.i("GattClientManager", "MTU negotiated with ${device.address}: $mtu")
                connectionPool.updateMtu(nodeId, mtu)
                // Discover services once MTU is agreed
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(AstraNetworkConfig.ASTRA_SERVICE_UUID)
                val rxChar = service?.getCharacteristic(AstraNetworkConfig.ASTRA_RX_CHARACTERISTIC_UUID)
                if (rxChar != null) {
                    // Enable notifications
                    gatt.setCharacteristicNotification(rxChar, true)
                    val descriptor = rxChar.getDescriptor(AstraNetworkConfig.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val success = gatt.writeDescriptor(descriptor)
                        AstraLog.d("GattClientManager", "NOTIFICATION_ENABLED on ${device.address}: $success")
                    }
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == AstraNetworkConfig.ASTRA_RX_CHARACTERISTIC_UUID) {
                    AstraLog.d("GattClientManager", "CHARACTERISTIC_CHANGED on ${gatt.device.address}")
                    val rawData = characteristic.value ?: return
                    try {
                        val slice = BleFrameCodec.decodeSlice(rawData)
                        AstraLog.d("GattClientManager", "PACKET_RECEIVED slice packetIndex=${slice.packetIndex} from $nodeId")
                        _incomingSlices.tryEmit(nodeId to slice)
                    } catch (e: Exception) {
                        AstraLog.e("GattClientManager", "PACKET_DROPPED Failed to decode incoming slice from $nodeId", e)
                    }
                }
            }
        }

        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        val handle = BleConnectionHandle(
            nodeId = nodeId,
            deviceAddress = deviceAddress,
            bluetoothGatt = gatt
        )
        connectionPool.addConnection(handle)
        return AstraResult.Success(Unit)
    }

    /**
     * Writes a single BLE slice with retry if Android Bluetooth stack is busy.
     */
    fun writeSlice(nodeId: NodeId, slice: BleSlice): AstraResult<Unit> {
        val handle = connectionPool.get(nodeId) ?: return AstraResult.Failure("Node $nodeId not connected")
        val gatt = handle.bluetoothGatt ?: return AstraResult.Failure("GATT handle null for $nodeId")

        val service = gatt.getService(AstraNetworkConfig.ASTRA_SERVICE_UUID)
            ?: return AstraResult.Failure("Astra service not discovered on $nodeId")
        val txChar = service.getCharacteristic(AstraNetworkConfig.ASTRA_TX_CHARACTERISTIC_UUID)
            ?: return AstraResult.Failure("TX characteristic not found on $nodeId")

        val data = BleFrameCodec.encodeSlice(slice)
        txChar.value = data
        txChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        var success = false
        var attempts = 0
        while (!success && attempts < 3) {
            attempts++
            success = gatt.writeCharacteristic(txChar)
            if (!success && attempts < 3) {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {}
            }
        }

        if (success) {
            AstraLog.d("GattClientManager", "WRITE_OK to node $nodeId length=${data.size} attempt=$attempts")
            handle.lastActivityTimestamp = System.currentTimeMillis()
        } else {
            AstraLog.w("GattClientManager", "WRITE_FAIL to node $nodeId length=${data.size} after $attempts attempts")
        }

        return if (success) AstraResult.Success(Unit) else AstraResult.Failure("Failed to initiate write on $nodeId")
    }

    /**
     * Writes multiple slices sequentially with coroutine-based pacing to prevent L2CAP buffer overflow.
     */
    suspend fun writeSlicesPaced(nodeId: NodeId, slices: List<BleSlice>, paceIntervalMs: Long = 10L): AstraResult<Unit> {
        for ((index, slice) in slices.withIndex()) {
            val res = writeSlice(nodeId, slice)
            if (res.isFailure) {
                AstraLog.w("GattClientManager", "Slice write failed at slice $index / ${slices.size} to $nodeId")
            }
            if (slices.size > 1 && paceIntervalMs > 0) {
                delay(paceIntervalMs)
            }
        }
        return AstraResult.Success(Unit)
    }
}

