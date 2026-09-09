package com.astramesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import com.astramesh.common.AstraLog
import com.astramesh.common.AstraResult
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hosts the Peripheral GATT Server responding to inbound connection requests and incoming frames.
 */
@SuppressLint("MissingPermission")
class GattServerManager(
    private val context: Context,
    private val localNodeId: NodeId
) {
    private var gattServer: BluetoothGattServer? = null
    private val _serverIncomingSlices = MutableSharedFlow<Pair<String, BleSlice>>(extraBufferCapacity = 128)
    val serverIncomingSlices: SharedFlow<Pair<String, BleSlice>> = _serverIncomingSlices.asSharedFlow()

    fun startServer(): AstraResult<Unit> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return AstraResult.Failure("BluetoothManager unavailable")

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                AstraLog.i("GattServerManager", "Server connection state for ${device.address}: $newState")
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (characteristic.uuid == AstraNetworkConfig.ASTRA_TX_CHARACTERISTIC_UUID) {
                    AstraLog.d("GattServerManager", "CHARACTERISTIC_CHANGED / write request received from ${device.address}")
                    try {
                        val slice = BleFrameCodec.decodeSlice(value)
                        AstraLog.d("GattServerManager", "PACKET_RECEIVED slice packetIndex=${slice.packetIndex} seq=${slice.sliceSeq}")
                        _serverIncomingSlices.tryEmit(device.address to slice)
                    } catch (e: Exception) {
                        AstraLog.e("GattServerManager", "PACKET_DROPPED Error decoding write request", e)
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        }

        val server = bluetoothManager.openGattServer(context, callback)
            ?: return AstraResult.Failure("Failed to open GATT server")

        // Build AstraMesh GATT Service
        val service = BluetoothGattService(
            AstraNetworkConfig.ASTRA_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        // TX Characteristic: Centrals write slices here
        val txChar = BluetoothGattCharacteristic(
            AstraNetworkConfig.ASTRA_TX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // RX Characteristic: Peripheral notifies Centrals here
        val rxChar = BluetoothGattCharacteristic(
            AstraNetworkConfig.ASTRA_RX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(txChar)
        service.addCharacteristic(rxChar)

        server.addService(service)
        gattServer = server

        return AstraResult.Success(Unit)
    }

    fun stopServer() {
        gattServer?.close()
        gattServer = null
    }
}
