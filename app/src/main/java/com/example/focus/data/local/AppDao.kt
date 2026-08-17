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

    @Upsert
    suspend fun upsertSelectedApp(app: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun removeSelectedApp(packageName: String)

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