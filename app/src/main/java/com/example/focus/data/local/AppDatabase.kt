package com.example.focus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SelectedAppEntity::class,
        DailyUsageEntity::class,
        FocusSessionEntity::class,
        TemporaryAllowanceEntity::class,
        FocusReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        /** we use a singleton to keep Room's update tracking consistent */
        @Volatile
        private var instance: AppDatabase? = null

        fun create(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "focus.db"
            ).build().also { instance = it }
        }
    }
}