package com.example.focus.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM selected_apps ORDER BY packageName")
    fun observeSelectedApps(): Flow<List<SelectedAppEntity>>

    @Upsert
    suspend fun upsert(app: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE packageName = :packageName")
    suspend fun remove(packageName: String)
}