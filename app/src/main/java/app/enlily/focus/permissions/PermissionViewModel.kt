package app.enlily.focus.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionUiState(
    /** The full list of permissions */
    val required: List<RequiredPermission> = requiredPermissions(),
    /** Currently missing permissions that need to be granted */
    val missing: List<RequiredPermission> = emptyList(),
    /** Whether the permissions are currently being checked; [missing] may be incorrect while this is true */
    val isChecking: Boolean = true
) {
    val allGranted: Boolean get() = !isChecking && missing.isEmpty()
}

class PermissionViewModel(application: Application) : AndroidViewModel(application) {
    /** Internal mutable state since we expose the ui state as an immutable [StateFlow] */
    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val required = _uiState.value.required
            // not sure if IO is the right dispatcher here, but checking permissions is probably not CPU intensive?
            val missing = withContext(Dispatchers.IO) {
                required.filterNot { it.isGranted(context) }
            }
            _uiState.value = PermissionUiState(
                required = required,
                missing = missing,
                isChecking = false
            )
        }
    }
}
