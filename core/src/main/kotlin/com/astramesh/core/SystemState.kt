package com.astramesh.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Battery and power management status.
 */
data class BatteryStatus(
    val levelPercent: Int, // 0..100
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean
) {
    val normalizedLevel: Float get() = (levelPercent.coerceIn(0, 100) / 100f)

    companion object {
        val UNKNOWN = BatteryStatus(100, isCharging = false, isPowerSaveMode = false)
    }
}

/**
 * Interface abstracting device battery state for battery-aware mesh routing.
 */
interface BatteryStatusProvider {
    val batteryStatus: StateFlow<BatteryStatus>
    fun getStatus(): BatteryStatus
}

/**
 * Connectivity state of the host device.
 */
sealed class ConnectivityState {
    data object Ready : ConnectivityState()
    data class BluetoothDisabled(val message: String) : ConnectivityState()
    data class PermissionsMissing(val missingPermissions: List<String>) : ConnectivityState()
    data class LocationDisabled(val message: String) : ConnectivityState()
    data class AirplaneModeEnabled(val message: String) : ConnectivityState()
}
