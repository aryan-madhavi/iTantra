package com.astramesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import com.astramesh.common.AstraLog
import com.astramesh.common.AstraResult
import com.astramesh.common.ByteUtils
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId

enum class BlePowerMode {
    LOW_POWER,
    BALANCED,
    LOW_LATENCY
}

/**
 * Manages BLE Peripheral Advertising with rotating ephemeral node IDs and adaptive duty cycles.
 */
@SuppressLint("MissingPermission")
class BleAdvertiserManager {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            AstraLog.i("BleAdvertiserManager", "BLE Advertising started successfully")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            AstraLog.e("BleAdvertiserManager", "BLE Advertising failed with error code: $errorCode")
            isAdvertising = false
        }
    }

    fun startAdvertising(nodeId: NodeId, powerMode: BlePowerMode = BlePowerMode.BALANCED): AstraResult<Unit> {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return AstraResult.Failure("Bluetooth adapter not available")

        AstraLog.d("BleAdvertiserManager", "Attempting to start BLE Advertising for node $nodeId")
        if (!adapter.isEnabled) {
            return AstraResult.Failure("Bluetooth adapter is disabled")
        }

        val leAdvertiser = adapter.bluetoothLeAdvertiser
            ?: return AstraResult.Failure("Device does not support BLE advertising")

        advertiser = leAdvertiser

        val (txPower, interval) = when (powerMode) {
            BlePowerMode.LOW_POWER -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW to AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
            BlePowerMode.BALANCED -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM to AdvertiseSettings.ADVERTISE_MODE_BALANCED
            BlePowerMode.LOW_LATENCY -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH to AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(interval)
            .setTxPowerLevel(txPower)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val serviceUuid = ParcelUuid(AstraNetworkConfig.ASTRA_SERVICE_UUID)
        val serviceData = ByteArray(8)
        ByteUtils.putUInt64BE(nodeId.value, serviceData, 0)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, serviceData)
            .build()

        leAdvertiser.startAdvertising(settings, data, advertiseCallback)
        return AstraResult.Success(Unit)
    }

    fun stopAdvertising() {
        if (isAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                AstraLog.e("BleAdvertiserManager", "Error stopping advertising", e)
            }
            isAdvertising = false
        }
    }
}
