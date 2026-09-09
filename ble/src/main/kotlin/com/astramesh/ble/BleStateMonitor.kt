package com.astramesh.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors Bluetooth adapter state changes and Airplane Mode.
 */
class BleStateMonitor(private val context: Context) {

    private val _isBluetoothEnabled = MutableStateFlow(isAdapterEnabled())
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                }
                IntentFilter("android.intent.action.AIRPLANE_MODE").toString() -> {
                    _isBluetoothEnabled.value = isAdapterEnabled()
                }
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        _isBluetoothEnabled.value = isAdapterEnabled()
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    private fun isAdapterEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        return adapter != null && adapter.isEnabled
    }
}
