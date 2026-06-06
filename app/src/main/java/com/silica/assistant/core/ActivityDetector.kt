package com.silica.assistant.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

class ActivityDetector(private val context: Context) {

    private val TAG = "ActivityDetector"

    private val usageStatsManager: UsageStatsManager? = run {
        try {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        } catch (_: Exception) {
            null
        }
    }

    private var lastKnownApp: String? = null

    fun getForegroundApp(): String? {
        val manager = usageStatsManager ?: return lastKnownApp

        return try {
            // Method 1: queryEvents — catat perpindahan foreground
            var currentApp: String? = null
            val endTime = System.currentTimeMillis()
            val events = manager.queryEvents(endTime - 30_000, endTime)
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    currentApp = event.packageName
                }
            }
            if (currentApp != null) {
                lastKnownApp = currentApp
                Log.d(TAG, "queryEvents -> $currentApp")
                return currentApp
            }

            // Method 2: queryUsageStats — cari app yg paling terakhir dipakai
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - 300_000,
                endTime
            )
            var recentApp: String? = null
            var recentTime = 0L
            for (usage in stats) {
                if (usage.lastTimeUsed > recentTime) {
                    recentTime = usage.lastTimeUsed
                    recentApp = usage.packageName
                }
            }
            if (recentApp != null) {
                lastKnownApp = recentApp
                Log.d(TAG, "queryUsageStats -> $recentApp")
                return recentApp
            }

            lastKnownApp
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
            lastKnownApp
        } catch (e: Exception) {
            Log.e(TAG, "query error", e)
            lastKnownApp
        }
    }

    fun isUsageStatsGranted(): Boolean {
        if (usageStatsManager == null) return false
        // Gunakan AppOpsManager untuk pengecekan yang lebih akurat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) return true
            } catch (_: Exception) {}
        }
        // Fallback: coba queryEvents
        return try {
            usageStatsManager.queryEvents(0, 1)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
