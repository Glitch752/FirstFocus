package com.example.focus.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.local.SelectedAppEntity
import com.example.focus.data.local.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    /** All installed apps, not yet filtered */
    val apps: List<InstalledApp> = emptyList(),
    /** The set of package names of the selected apps */
    val selectedPackages: Set<String> = emptySet(),
    /** The current search query */
    val searchQuery: String = "",
    /** Whether the app list is still loading */
    val isLoading: Boolean = false
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = InstalledAppRepository(application)
    private val dao = AppDatabase.create(application).appDao()
    /** Internal mutable state since we expose the state as an immutable [StateFlow] */
    private val _uiState = MutableStateFlow(AppSelectionUiState())
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()

    init {
        refreshApps()
        viewModelScope.launch {
            // Update our selected apps in the UI whenever the database changes
            dao.observeSelectedApps().collectLatest { selected ->
                _uiState.value = _uiState.value.copy(selectedPackages = selected.asSequence().map { it.packageName }.toSet())
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // kotlin is so fancy omg this is so easy
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.getLauncherApps()
            }
            _uiState.value = _uiState.value.copy(apps = apps, isLoading = false)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            if (packageName in _uiState.value.selectedPackages) dao.removeSelectedApp(packageName)
            else dao.upsertSelectedApp(SelectedAppEntity(packageName))
        }
    }
}