package com.example.focus.data.settings

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.focusDataStore by preferencesDataStore(name = "focus_settings")

object SettingsKeys {
    val preOpenCountdownSeconds = intPreferencesKey("pre_open_countdown_seconds")
    val dailyDistractingTargetMillis = longPreferencesKey("daily_distracting_target_millis")
    val defaultFocusDurationMillis = longPreferencesKey("default_focus_duration_millis")
}