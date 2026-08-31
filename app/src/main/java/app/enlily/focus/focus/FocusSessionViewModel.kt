package app.enlily.focus.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.enlily.focus.data.local.FocusSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = FocusSessionManager.get(application)

    /**
     * I honestly don't fully understand how [StateFlow] works, but this seems to be the correct way to expose
     * a [Flow] to the UI?
     */

    /** The active focus session, if any */
    val active: StateFlow<FocusSessionEntity?> = manager.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** Whether focus sessions should automatically end when their planned duration expires */
    val automaticallyEnd: StateFlow<Boolean> = manager.automaticallyEnd
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun start(durationMillis: Long) = viewModelScope.launch(Dispatchers.IO) {
        manager.start(durationMillis)
    }

    fun stop() = viewModelScope.launch(Dispatchers.IO) {
        manager.stop()
    }

    fun setAutomaticallyEnd(enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        manager.setAutomaticallyEnd(enabled)
    }
}
