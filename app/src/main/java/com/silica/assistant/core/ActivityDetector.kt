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
            val endTime = System.currentTimeMillis()
            
            // Debug: check queryEvents range
            val events = manager.queryEvents(endTime - 60_000, endTime)
            var currentApp: String? = null
            var count = 0
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                count++
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    currentApp = event.packageName
                }
            }
            Log.d(TAG, "queryEvents processed $count events. Found: $currentApp")
            
            if (currentApp != null) {
                lastKnownApp = currentApp
                return currentApp
            }

            // Method 2: queryUsageStats — cari app yg paling terakhir dipakai
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                endTime - 600_000,
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
            Log.d(TAG, "queryUsageStats checked ${stats.size} apps. Most recent: $recentApp at $recentTime")
            
            if (recentApp != null) {
                lastKnownApp = recentApp
                return recentApp
            }

            Log.d(TAG, "getForegroundApp returned null. lastKnownApp=$lastKnownApp")
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
