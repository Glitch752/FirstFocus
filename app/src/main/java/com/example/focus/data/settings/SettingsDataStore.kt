package com.example.focus.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.focusDataStore by preferencesDataStore(name = "focus_settings")

object SettingsKeys {
    val usageHistoryLastUpdated = longPreferencesKey("usage_history_last_updated")
    val preOpenCountdownSeconds = intPreferencesKey("pre_open_countdown_seconds")
    val dailyDistractingTargetMillis = longPreferencesKey("daily_distracting_target_millis")
    val defaultFocusDurationMillis = longPreferencesKey("default_focus_duration_millis")
    val automaticallyEndFocusSessions = booleanPreferencesKey("automatically_end_focus_sessions")
    val grayscaleDuringFocus = booleanPreferencesKey("grayscale_during_focus")
    /** Whether OEM-specific notification permission prompts have been dismissed */
    val oemNotificationPermissionDismissed = booleanPreferencesKey("oem_notification_permission_dismissed")
}

/** The set of settings to persist in backups */
val backupSettingsKeys: Set<Preferences.Key<*>> = setOf(
    SettingsKeys.usageHistoryLastUpdated,
    SettingsKeys.preOpenCountdownSeconds,
    SettingsKeys.dailyDistractingTargetMillis,
    SettingsKeys.defaultFocusDurationMillis,
    SettingsKeys.automaticallyEndFocusSessions,
    SettingsKeys.grayscaleDuringFocus,
    SettingsKeys.oemNotificationPermissionDismissed
)