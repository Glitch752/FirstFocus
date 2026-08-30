package com.example.focus.data.backup

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.local.DB_VERSION
import com.example.focus.data.settings.backupSettingsKeys
import com.example.focus.data.settings.focusDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Handles exporting and importing the database and settings to a simple zip file backup format.
 * We want to be able to import and export all the data with as minimal maintenance as possible, so we rely on the
 * existing database migrations by directly copying and importing the SQLite database file.
 */
class BackupRepository(private val context: Context) {
    /** Export the database and settings to the given file. The file will be overwritten if it exists. */
    suspend fun export(target: File) = withContext(Dispatchers.IO) {
        val database = AppDatabase.create(context)
        // Force a checkpoint to ensure all data is written to the database file before we copy it
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }

        // JSON metadata including the database version and settings
        val settings = context.focusDataStore.data.first()
        val data = JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("databaseVersion", database.openHelper.readableDatabase.version)
            put("settings", settingsToJson(settings))
        }

        // construct our output zip file
        target.outputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                // JSON metadata file
                zip.putNextEntry(ZipEntry(DATA_FILE))
                zip.write(data.toString().toByteArray())
                zip.closeEntry()

                // directly copy the SQLite database file
                zip.putNextEntry(ZipEntry(DB_FILE))
                File(context.getDatabasePath(AppDatabase.DATABASE_FILE).path)
                    .inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Import the database and settings from the given file. The current database and settings will be replaced. */
    suspend fun import(source: File) = withContext(Dispatchers.IO) {
        // We create a database that we try to load as the main app database, which will automatically handle
        // migrations for us if necessary. Then, once it imports successfully, we replace the current database with
        // the imported one.
        val staging = File(context.cacheDir, "room-import-${System.currentTimeMillis()}.db")
        try {
            ZipFile(source).use { zip ->
                // Load the metadata and validate the format and version
                val data = JSONObject(zip.getInputStream(zip.getEntry(DATA_FILE)).reader().readText())
                require(data.optString("format") == FORMAT) { "Not a Focus backup" }
                require(data.optInt("formatVersion") == FORMAT_VERSION) { "Unsupported backup format" }
                require(data.getInt("databaseVersion") >= DB_VERSION) { "Unsupported database version" }

                // Copy the database file in the zip to our staging database
                zip.getInputStream(zip.getEntry(DB_FILE))
                    .use { input -> staging.outputStream().use { input.copyTo(it) } }

                // Validate the database and run migrations if necessary
                validateAndMigrate(staging)

                // Close the real database and replace it with the imported one
                AppDatabase.close()
                val live = context.getDatabasePath(AppDatabase.DATABASE_FILE)
                staging.copyTo(live, overwrite = true)

                // Restore the settings from the backup
                restoreSettings(data.getJSONObject("settings"))
            }
        } finally {
            staging.delete()
        }
    }

    /** Validates the given database file and runs migrations if necessary. Throws an exception if the database is invalid. */
    private fun validateAndMigrate(file: File) {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.path).build()
        db.openHelper.readableDatabase.query("SELECT 1").use { }
        db.close()
    }

    /** Restores the settings from the given JSON object, replacing the current settings. */
    private suspend fun restoreSettings(json: JSONObject) {
        context.focusDataStore.edit { prefs ->
            prefs.clear()
            backupSettingsKeys.forEach { key ->
                if (json.has(key.name)) {
                    @Suppress("UNCHECKED_CAST") // hope and pray
                    prefs[key as Preferences.Key<Any>] = json.get(key.name)
                }
            }
        }
    }
    /** Converts the given settings to a JSON object, including only the keys in [backupSettingsKeys]. */
    private fun settingsToJson(p: Preferences) = JSONObject().apply {
        backupSettingsKeys.forEach { key ->
            p[key]?.let { put(key.name, it) }
        }
    }

    companion object {
        const val FORMAT = "focus-backup"
        const val FORMAT_VERSION = 1

        const val DATA_FILE = "data.json"
        const val DB_FILE = "room.db"
    }
}
