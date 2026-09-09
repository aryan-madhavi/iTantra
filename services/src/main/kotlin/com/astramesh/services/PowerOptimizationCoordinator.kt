package com.astramesh.services

import android.content.Context
import android.os.PowerManager
import com.astramesh.common.AstraLog

/**
 * Coordinates partial WakeLocks and battery conservation modes to keep the BLE mesh alive
 * without depleting device battery.
 */
class PowerOptimizationCoordinator(
    private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquireTemporaryWakeLock(tag: String = "AstraMesh:RelayLock", timeoutMillis: Long = 5000L) {
        if (wakeLock == null) {
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            wakeLock?.acquire(timeoutMillis)
            AstraLog.d("PowerOptimization", "Acquired wake lock for $timeoutMillis ms")
        } catch (e: Exception) {
            AstraLog.e("PowerOptimization", "Failed to acquire wake lock", e)
        }
    }

    fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AstraLog.d("PowerOptimization", "Released wake lock")
            }
        } catch (e: Exception) {
            AstraLog.e("PowerOptimization", "Error releasing wake lock", e)
        }
    }

    fun isDeviceInDozeMode(): Boolean {
        return powerManager?.isDeviceIdleMode == true
    }
}
