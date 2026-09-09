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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages Central GATT Client interactions: connection, MTU negotiation, PHY, notifications, and writing frames.
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

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    AstraLog.i("GattClientManager", "Connected to GATT peer ${device.address}")
                    // Negotiate high MTU
                    gatt.requestMtu(AstraNetworkConfig.REQUESTED_BLE_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    AstraLog.w("GattClientManager", "Disconnected from GATT peer ${device.address}")
                    connectionPool.removeConnection(nodeId)
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
        AstraLog.d("GattClientManager", "BLE_WRITE to node $nodeId length=${data.size}")
        val success = gatt.writeCharacteristic(txChar)
        if (success) {
            AstraLog.d("GattClientManager", "WRITE_SUCCESS to node $nodeId")
        } else {
            AstraLog.w("GattClientManager", "WRITE_FAILED to node $nodeId")
        }

        return if (success) AstraResult.Success(Unit) else AstraResult.Failure("Failed to initiate write on $nodeId")
    }
}
