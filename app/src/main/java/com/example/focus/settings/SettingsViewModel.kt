package com.example.focus.settings

import android.app.Application
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import com.example.focus.grayscale.GrayscaleController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val grayscaleController = GrayscaleController(application)
    /** The number of seconds to wait before allowing the user to open a distracting app */
    val countdownSeconds = application.focusDataStore.data
        .map { it[SettingsKeys.preOpenCountdownSeconds] ?: 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val automaticallyEnd = application.focusDataStore.data
        .map { it[SettingsKeys.automaticallyEndFocusSessions] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val grayscaleDuringFocus = application.focusDataStore.data
        .map { it[SettingsKeys.grayscaleDuringFocus] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    // grayscale mode

    fun setGrayscaleDuringFocus(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().focusDataStore.edit {
                it[SettingsKeys.grayscaleDuringFocus] = enabled
            }
        }
    }

    fun hasSecureSettingsAccess() = grayscaleController.hasSecureSettingsAccess()
    fun hasShizukuPermission() = grayscaleController.shizukuPermissionGranted()
    fun hasShizukuAppInstalled() = grayscaleController.shizukuAppInstalled()
    fun requestShizukuPermission(onResult: (Boolean) -> Unit) =
        viewModelScope.launch { onResult(grayscaleController.requestShizukuPermission()) }
    fun isShizukuRunning() = grayscaleController.shizukuRunning()
    fun grantThroughShizuku(onResult: (Boolean) -> Unit) =
        viewModelScope.launch { onResult(grayscaleController.grantThroughShizuku()) }

    private fun openWebsiteIntent(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        intent.data = url.toUri()
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }
    fun openAdbInstructions() = openWebsiteIntent("https://developer.android.com/tools/adb#Enabling")
    fun openShizukuInstructions() = openWebsiteIntent("https://shizuku.rikka.app/download")

    /** Opens the Shizuku app to the start page, where the user can grant start it */
    fun openShizukuStart() {
        val application = getApplication<Application>()
        val intent = application.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}
