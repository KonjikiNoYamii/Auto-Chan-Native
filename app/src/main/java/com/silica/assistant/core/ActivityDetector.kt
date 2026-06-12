package com.silica.assistant.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.launch

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

    private var lastSpontaneousCommentTime = 0L
    private val SPONTANEOUS_INTERVAL = 15 * 60 * 1000 // 15 minutes
    private val activityScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    data class Automation(
        val action: String, // GAME_MODE, BRIGHTNESS_MIN, BRIGHTNESS_MAX, VOLUME_MUTE, VOLUME_MAX
        val description: String
    )

    private val appAutomations = mapOf(
        "com.miHoYo.GenshinImpact" to Automation("GAME_MODE", "Mengaktifkan Game Mode untuk Genshin Impact..."),
        "com.mobile.legends" to Automation("GAME_MODE", "Persiapan tempur! Mengaktifkan Game Mode..."),
        "com.netflix.mediaclient" to Automation("BRIGHTNESS_MAX", "Menyesuaikan layar agar nonton lebih nyaman..."),
        "com.google.android.youtube" to Automation("VOLUME_UP", "Menambah volume untuk kenyamanan nonton..."),
        "com.android.settings" to Automation("BRIGHTNESS_MAX", "Menerangkan layar untuk pengaturan..."),
        "com.whatsapp" to Automation("NONE", ""),
    )

    fun getAutomationForApp(packageName: String): Automation? {
        // Check exact match or prefixes for known games
        return appAutomations[packageName] ?: if (com.silica.assistant.overlay.GameModeManager.isGame(context, packageName)) {
            Automation("GAME_MODE", "Mendeteksi game, mengaktifkan Game Mode otomatis... ♪")
        } else null
    }

    fun hasUsedAppRecently(packageName: String, hours: Int = 3, minDurationMs: Long = 60_000): Boolean {
        val manager = usageStatsManager ?: return false
        try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (hours * 3600_000L)
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            
            val appStats = stats.find { it.packageName == packageName }
            return (appStats?.totalTimeInForeground ?: 0L) >= minDurationMs
        } catch (e: Exception) {
            Log.e(TAG, "Error checking recent usage", e)
            return false
        }
    }

    fun checkAndTriggerSpontaneousComment(appName: String) {
        val now = System.currentTimeMillis()
        if (now - lastSpontaneousCommentTime > SPONTANEOUS_INTERVAL) {
            lastSpontaneousCommentTime = now
            activityScope.launch {
                val comment = com.silica.assistant.core.llm.LlmClient.generateActivityComment(appName, false)
                if (comment != null) {
                    com.silica.assistant.core.overlay.OverlayEventBus.onBubble?.invoke(comment)
                }
            }
        }
    }
}

