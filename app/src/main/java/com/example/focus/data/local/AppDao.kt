package com.example.focus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // focus reminders

    @Query("SELECT * FROM focus_reminders ORDER BY hour, minute, id")
    fun observeFocusReminders(): Flow<List<FocusReminderEntity>>

    @Query("SELECT * FROM focus_reminders")
    suspend fun focusReminders(): List<FocusReminderEntity>

    @Insert
    suspend fun insertFocusReminder(reminder: FocusReminderEntity): Long

    @Upsert
    suspend fun upsertFocusReminder(reminder: FocusReminderEntity)

    @Query("DELETE FROM focus_reminders WHERE id = :id")
    suspend fun deleteFocusReminder(id: Long)

    // selected apps

    @Query("SELECT * FROM selected_apps ORDER BY packageName")
    fun observeSelectedApps(): Flow<List<SelectedAppEntity>>

    @Query("SELECT packageName FROM selected_apps")
    suspend fun selectedPackageNames(): List<String>

    @Upsert
    suspend fun upsertSelectedApp(app: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun removeSelectedApp(packageName: String)

    // daily usage history

    @Query("SELECT * FROM daily_usage WHERE date >= :since ORDER BY date DESC, foregroundMillis DESC")
    fun observeDailyUsage(since: String): Flow<List<DailyUsageEntity>>

    @Query("DELETE FROM daily_usage WHERE date = :date")
    suspend fun clearDailyUsage(date: String)

    @Insert
    suspend fun insertDailyUsage(entries: List<DailyUsageEntity>)

    /**
     * Get a summary of daily usage for a given date range.
     * Both fromDate and toDate are inclusive.
     */
    @Query("""
        SELECT date,
            SUM(foregroundMillis) AS totalForegroundMillis,
            SUM(CASE
                WHEN packageName IN (SELECT packageName FROM selected_apps) THEN foregroundMillis
                ELSE 0
            END) AS distractingForegroundMillis
        FROM daily_usage
        WHERE date BETWEEN :fromDate AND :toDate
        GROUP BY date
        ORDER BY date
    """)
    suspend fun usageTotals(fromDate: String, toDate: String): List<DailyUsageTotals>

    /**
     * Get an aggregation of the top apps across a certain date range.
     * Both fromDate and toDate are inclusive.
     */
    @Query("""
        SELECT packageName,
            SUM(foregroundMillis) AS foregroundMillis, 
            CASE WHEN packageName IN (SELECT packageName FROM selected_apps) THEN 1 ELSE 0 END AS isDistracting
        FROM daily_usage
        WHERE date BETWEEN :fromDate AND :toDate AND foregroundMillis > :minTime
        GROUP BY packageName
        ORDER BY foregroundMillis DESC
        LIMIT 8
    """)
    suspend fun topApps(fromDate: String, toDate: String, minTime: Long = 1000L * 60L): List<AppUsageTotal>

    @Query("SELECT * FROM daily_usage_status WHERE date >= :since")
    suspend fun dailyUsageStatuses(since: String): List<DailyUsageStatusEntity>

    @Upsert
    suspend fun upsertDailyUsageStatus(status: DailyUsageStatusEntity)

    // allowances

    @Query("SELECT * FROM temporary_allowances WHERE packageName = :packageName AND expiresAtMillis > :now LIMIT 1")
    suspend fun activeAllowance(packageName: String, now: Long): TemporaryAllowanceEntity?

    @Upsert
    suspend fun upsertAllowance(allowance: TemporaryAllowanceEntity)

    @Query("DELETE FROM temporary_allowances WHERE packageName = :packageName")
    suspend fun removeAllowance(packageName: String)

    // focus sessions

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeActiveFocusSession(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status = 'ACTIVE' ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun activeFocusSession(): FocusSessionEntity?

    /**
     * Get a summary of focus sessions for a given date range.
     * fromDate and toDate are both inclusive.  
     * 
     * Complex code isn't something to be proud of, but I'm proud of this SQL.
     * Hopefully it's not completely incomprehensible.
     */
    @Query("""
        WITH RECURSIVE calendar(day_date, day_start, day_end) AS (
            -- generate a continuous list of days between :fromDate and :toDate with millisecond start and end boundaries
            -- I'm not sure if using a recursive cte is a performant approach, but I assume it's optimized?
            SELECT
                :fromDate,
                CAST(strftime('%s', :fromDate) AS INTEGER) * 1000,
                CAST(strftime('%s', :fromDate, '+1 day') AS INTEGER) * 1000
            UNION ALL SELECT
                date(day_date, '+1 day'),
                CAST(strftime('%s', day_date, '+1 day') AS INTEGER) * 1000,
                CAST(strftime('%s', day_date, '+2 days') AS INTEGER) * 1000
            FROM calendar
            WHERE day_date < :toDate
        ),
        prepared_sessions AS (
            -- just calculate the planned end time for easier math later
            SELECT startedAtMillis, endedAtMillis, status,
                startedAtMillis + plannedDurationMillis AS plannedEndMillis,
                -- the latest relevant time for this session is the later of the actual end or planned end
                -- used for filtering sessions that overlap with a given day
                MAX(endedAtMillis, startedAtMillis + plannedDurationMillis) AS latestTimeMillis
            FROM focus_sessions
            WHERE endedAtMillis IS NOT NULL -- only include sessions that have ended
        )
        
        -- calculate metrics per day by joining the calendar with the prepared sessions using simple interval overlap math
        SELECT c.day_date AS date,
            -- finishedMillis is the overlap of [day_start, day_end] with [startedAtMillis, endedAtMillis]
            COALESCE(SUM(
                MAX(0, MIN(c.day_end, s.endedAtMillis) - MAX(c.day_start, s.startedAtMillis))
            ), 0) AS finishedMillis,
            -- skippedMillis is the overlap of [day_start, day_end] with [endedAtMillis, plannedEndMillis]
            -- (only for cancelled sessions)
            COALESCE(SUM(CASE
                WHEN s.status = 'CANCELLED' THEN MAX(0, MIN(c.day_end, s.plannedEndMillis) - MAX(c.day_start, s.endedAtMillis))
                ELSE 0
            END), 0) AS skippedMillis
        FROM calendar c
        -- join sessions that overlap with the calendar day
        LEFT JOIN prepared_sessions s ON s.startedAtMillis < c.day_end AND s.latestTimeMillis > c.day_start
        GROUP BY c.day_date
        ORDER BY c.day_date
    """)
    suspend fun focusSummaries(fromDate: String, toDate: String): List<FocusSessionSummary>

    @Insert
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET endedAtMillis = :endedAt, status = :status WHERE id = :id AND status = 'ACTIVE'")
    suspend fun completeFocusSessionIfActive(id: Long, endedAt: Long, status: FocusSessionStatus = FocusSessionStatus.COMPLETED)
}