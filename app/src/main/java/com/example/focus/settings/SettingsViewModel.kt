package com.example.focus.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    /** The number of seconds to wait before allowing the user to open a distracting app */
    val countdownSeconds = application.focusDataStore.data
        .map { it[SettingsKeys.preOpenCountdownSeconds] ?: 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val automaticallyEnd = application.focusDataStore.data
        .map { it[SettingsKeys.automaticallyEndFocusSessions] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setCountdownSeconds(seconds: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().focusDataStore.edit {
                it[SettingsKeys.preOpenCountdownSeconds] = seconds
            }
        }
    }

    fun setAutomaticallyEnd(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().focusDataStore.edit {
                it[SettingsKeys.automaticallyEndFocusSessions] = enabled
            }
        }
    }
}