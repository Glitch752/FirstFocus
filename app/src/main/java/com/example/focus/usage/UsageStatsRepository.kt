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

    fun today(selectedPackages: Set<String>): UsageSummary {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val byPackage = usageStatsManager()
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .associate { it.packageName to it.totalTimeInForeground }
        return UsageSummary(
            totalMillis = byPackage.values.sum(),
            distractingMillis = byPackage.filterKeys { it in selectedPackages }.values.sum(),
            byPackage = byPackage
        )
    }

    private fun usageStatsManager() =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
}