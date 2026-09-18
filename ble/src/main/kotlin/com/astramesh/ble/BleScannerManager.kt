package com.astramesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import com.astramesh.common.AstraLog
import com.astramesh.common.AstraResult
import com.astramesh.common.ByteUtils
import com.astramesh.core.AstraNetworkConfig
import com.astramesh.core.NodeId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DiscoveredBlePeer(
    val nodeId: NodeId,
    val deviceAddress: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages Central BLE Scanning with hardware scan filters and adaptive duty cycles.
 */
@SuppressLint("MissingPermission")
class BleScannerManager(private val context: Context) {

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var isReceiverRegistered = false
    private var currentPowerMode: BlePowerMode = BlePowerMode.BALANCED

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    if (!isScanning) {
                        AstraLog.i("BleScannerManager", "Bluetooth turned ON. Restarting BLE discovery.")
                        startScanning(currentPowerMode)
                    }
                } else if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    AstraLog.w("BleScannerManager", "Bluetooth turned OFF. Pausing BLE discovery.")
                    isScanning = false
                }
            }
        }
    }

    private val _discoveredPeers = MutableSharedFlow<DiscoveredBlePeer>(extraBufferCapacity = 64)
    val discoveredPeers: SharedFlow<DiscoveredBlePeer> = _discoveredPeers.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val record = result.scanRecord ?: return

            val serviceUuid = ParcelUuid(AstraNetworkConfig.ASTRA_SERVICE_UUID)
            val serviceData = record.getServiceData(serviceUuid)

            if (serviceData != null && serviceData.size >= 8) {
                val rawNodeId = ByteUtils.getUInt64BE(serviceData, 0)
                AstraLog.d("BleScannerManager", "Discovered BLE Peer advertisement from NodeId: $rawNodeId, Address: ${result.device.address}, RSSI: ${result.rssi}")
                val peer = DiscoveredBlePeer(
                    nodeId = NodeId(rawNodeId),
                    deviceAddress = result.device.address,
                    rssi = result.rssi
                )
                _discoveredPeers.tryEmit(peer)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            AstraLog.e("BleScannerManager", "BLE Scan failed with code: $errorCode")
            isScanning = false
        }
    }

    fun startScanning(powerMode: BlePowerMode = BlePowerMode.BALANCED): AstraResult<Unit> {
        currentPowerMode = powerMode
        
        if (!isReceiverRegistered) {
            context.registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            isReceiverRegistered = true
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) return AstraResult.Failure("Bluetooth adapter unavailable")
        if (!adapter.isEnabled) return AstraResult.Failure("Bluetooth adapter disabled")

        val leScanner = adapter.bluetoothLeScanner ?: return AstraResult.Failure("BLE Scanner unavailable")
        scanner = leScanner

        val scanMode = when (powerMode) {
            BlePowerMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
            BlePowerMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            BlePowerMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
        }

        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .setReportDelay(0)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(AstraNetworkConfig.ASTRA_SERVICE_UUID))
            .build()

        try {
            leScanner.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            AstraLog.d("BleScannerManager", "BLE Scanning started successfully")
        } catch (e: Exception) {
            AstraLog.e("BleScannerManager", "Exception during startScan", e)
            return AstraResult.Failure("Exception starting scan: ${e.message}")
        }
        return AstraResult.Success(Unit)
    }

    fun stopScanning() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(btStateReceiver)
            } catch (e: Exception) {}
            isReceiverRegistered = false
        }
        
        if (isScanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                AstraLog.e("BleScannerManager", "Error stopping scan", e)
            }
            isScanning = false
        }
    }
}
