package com.example.focus.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
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

    @Insert
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET endedAtMillis = :endedAt, status = :status WHERE id = :id AND status = 'ACTIVE'")
    suspend fun completeFocusSessionIfActive(id: Long, endedAt: Long, status: FocusSessionStatus = FocusSessionStatus.COMPLETED)
}