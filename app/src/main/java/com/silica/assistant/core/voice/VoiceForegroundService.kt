package com.silica.assistant.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.silica.assistant.R

class VoiceForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(1, createNotification(), type)
            } else {
                startForeground(1, createNotification())
            }
        } catch (_: SecurityException) {
            // Android 13+ requires POST_NOTIFICATIONS; fallback silently
        }
    }

    // =========================
    // NOTIFICATION (WAJIB)
    // =========================
    private fun createNotification(): Notification {

        val channelId = "voice_channel"

        val channel =
                NotificationChannel(
                        channelId,
                        "Voice Assistant",
                        NotificationManager.IMPORTANCE_LOW
                )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
                .setContentTitle("Silica Assistant Active")
                .setContentText("Tap overlay untuk voice command")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
    }

    // =========================
    // LIFECYCLE
    // =========================
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
