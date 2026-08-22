package com.example.focus.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "selected_apps")
data class SelectedAppEntity(
    @PrimaryKey val packageName: String
)

@Entity(tableName = "daily_usage", primaryKeys = ["date", "packageName"])
data class DailyUsageEntity(
    val date: String,
    val packageName: String,
    val foregroundMillis: Long
)

/** This isn't a room entity, but it's the result of usage totals queries */
data class DailyUsageTotals(
    val date: String,
    val totalForegroundMillis: Long,
    val distractingForegroundMillis: Long
)
/** This isn't a room entity, but the result of top apps queries */
data class AppUsageTotal(val packageName: String, val foregroundMillis: Long, val isDistracting: Boolean)

enum class DailyUsageCompleteness { PARTIAL, FULL }

@Entity(tableName = "daily_usage_status")
data class DailyUsageStatusEntity(
    @PrimaryKey val date: String,
    /** I wanted to call this status, but it makes this hard to read, so completeness it is */
    val completeness: DailyUsageCompleteness,
    val updatedAtMillis: Long
)

/** This isn't a room entity, but the result of focus session queries */
data class FocusSessionSummary(val date: String, val finishedMillis: Long, val skippedMillis: Long)

enum class FocusSessionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

@Entity(
    tableName = "focus_sessions",
    indices = [
        // speeds up our aggregation queries significantly
        Index(value = ["startedAtMillis", "endedAtMillis"])
    ]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val plannedDurationMillis: Long,
    val status: FocusSessionStatus
)

@Entity(tableName = "temporary_allowances")
data class TemporaryAllowanceEntity(
    @PrimaryKey val packageName: String,
    val expiresAtMillis: Long
)

@Entity(tableName = "focus_reminders")
data class FocusReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val daysOfWeek: String,
    val durationMillis: Long
)