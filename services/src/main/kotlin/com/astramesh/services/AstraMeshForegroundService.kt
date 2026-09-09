package com.astramesh.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.astramesh.common.AstraLog
import com.astramesh.mesh.MeshEngine

/**
 * Foreground service keeping the BLE mesh radio stack alive during background execution.
 */
class AstraMeshForegroundService : Service() {

    private lateinit var powerCoordinator: PowerOptimizationCoordinator

    companion object {
        const val CHANNEL_ID = "astra_mesh_service_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, AstraMeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AstraMeshForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerCoordinator = PowerOptimizationCoordinator(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("AstraMesh Active - Listening for peers"))
        AstraLog.i("AstraMeshService", "Foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        powerCoordinator.acquireTemporaryWakeLock(timeoutMillis = 10_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        powerCoordinator.releaseWakeLock()
        AstraLog.i("AstraMeshService", "Foreground service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AstraMesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps AstraMesh BLE decentralized network alive"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AstraMesh Network")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
