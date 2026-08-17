package com.example.focus.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

data class UsageSummary(
    val totalMillis: Long = 0,
    val distractingMillis: Long = 0,
    val byPackage: Map<String, Long> = emptyMap()
)

class UsageStatsRepository(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val end = System.currentTimeMillis()
        val start = end - 60_000L
        return usageStatsManager().queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).isNotEmpty()
    }

    /** Return a usage summary for the past day */
    fun today(selectedPackages: Set<String>): UsageSummary {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()

        // queryUsageStats() with INTERVAL_DAILY is a hint, I suppose, since it doesn't provide the correct day-based
        // data that we want. it's a bit annoying, but we need to use usage events instead to have stricter bounds

        val durations = mutableMapOf<String, Long>()
        val events = usageStatsManager().queryEvents(start, end)
        var foregroundPackage: String? = null
        var foregroundSince = start
        val event = android.app.usage.UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            val isForeground =
                event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
            val isBackground =
                event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND

            if (isForeground) {
                foregroundPackage?.let { durations[it] = (durations[it] ?: 0L) + event.timeStamp - foregroundSince }
                foregroundPackage = packageName
                foregroundSince = event.timeStamp.coerceAtLeast(start)
            } else if (isBackground && packageName == foregroundPackage) {
                durations[packageName] = (durations[packageName] ?: 0L) + event.timeStamp - foregroundSince
                foregroundPackage = null
            }
        }
        foregroundPackage?.let { durations[it] = (durations[it] ?: 0L) + end - foregroundSince }

        val byPackage = durations.filterValues { it > 0 }
        return UsageSummary(
            totalMillis = byPackage.values.sum(),
            distractingMillis = byPackage.filterKeys { it in selectedPackages }.values.sum(),
            byPackage = byPackage
        )
    }

    private fun usageStatsManager() =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
}