package com.silica.assistant.core

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class ActivityDetector(private val context: Context) {

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
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 30_000

        return try {
            val events = manager.queryEvents(beginTime, endTime)
            var currentApp: String? = null
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    currentApp = event.packageName
                }
            }
            if (currentApp != null) lastKnownApp = currentApp
            currentApp ?: lastKnownApp
        } catch (_: SecurityException) {
            lastKnownApp
        }
    }

    fun isUsageStatsGranted(): Boolean {
        val manager = usageStatsManager ?: return false
        return try {
            manager.queryEvents(0, 1)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
