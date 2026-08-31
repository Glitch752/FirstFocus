package app.enlily.focus.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import app.enlily.focus.data.local.DailyUsageEntity
import java.time.LocalDate
import java.time.ZoneId

data class UsageSummary(
    val totalMillis: Long = 0,
    val distractingMillis: Long = 0,
    val byPackage: Map<String, Long> = emptyMap()
)

class UsageStatsRepository(private val context: Context) {
    companion object {
        /** The number of days for which we'll regenerate historical data */
        const val HISTORY_DAYS = 7
    }

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
        val byPackage = usageEventsBetween(start, end).orEmpty()
        return UsageSummary(
            totalMillis = byPackage.values.sum(),
            distractingMillis = byPackage.filterKeys { it in selectedPackages }.values.sum(),
            byPackage = byPackage
        )
    }

    /**
     * Get the best available usage data for the given date. This will use usage events if available but
     * fall back to aggregate stats
     */
    fun usageForDate(date: LocalDate): List<DailyUsageEntity> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        
        val durations = usageEventsBetween(start, end).orEmpty()
        return durations.map { (packageName, millis) -> DailyUsageEntity(date.toString(), packageName, millis) }
    }

    /**
     * Return usage foreground durations based on events between the given times.
     * UsageStatsManager only provides events for the past "few" days, however, so this may not return anything.
     */
    private fun usageEventsBetween(start: Long, end: Long): Map<String, Long>? {
        val durations = mutableMapOf<String, Long>()
        var foregroundPackage: String? = null
        var foregroundSince = start

        val events = usageStatsManager().queryEvents(start, end)
        val event = android.app.usage.UsageEvents.Event()
        var foundEvent = false
        while (events.hasNextEvent()) {
            foundEvent = true

            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            val foreground = event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
            val background = event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED

            if (foreground) {
                foregroundPackage?.let { durations[it] = (durations[it] ?: 0) + event.timeStamp - foregroundSince }
                foregroundPackage = packageName
                foregroundSince = event.timeStamp.coerceIn(start, end)
            } else if (background && packageName == foregroundPackage) {
                durations[packageName] = (durations[packageName] ?: 0) + event.timeStamp - foregroundSince
                foregroundPackage = null
            }
        }

        foregroundPackage?.let { durations[it] = (durations[it] ?: 0) + end - foregroundSince }
        return if (foundEvent) durations.filterValues { it > 0 } else null
    }

    private fun usageStatsManager() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
}