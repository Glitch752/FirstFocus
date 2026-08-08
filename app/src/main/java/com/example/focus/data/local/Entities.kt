package com.example.focus.data.local

import androidx.room.Entity
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

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val plannedDurationMillis: Long,
    val status: String
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