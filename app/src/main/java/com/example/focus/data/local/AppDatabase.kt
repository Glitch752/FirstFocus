package com.example.focus.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SelectedAppEntity::class,
        DailyUsageEntity::class,
        DailyUsageStatusEntity::class,
        FocusSessionEntity::class,
        TemporaryAllowanceEntity::class,
        FocusReminderEntity::class
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
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