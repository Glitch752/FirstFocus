package com.example.focus.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State for app usage data */
data class UsageUiState(
    /** The latest summary of data from the usage stats service */
    val summary: UsageSummary = UsageSummary(),
    /** The set of package names that are selected as distracting */
    val selectedPackages: Set<String> = emptySet(),
    /** Whether the summary data is being loaded */
    val isLoading: Boolean = false
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsageStatsRepository(application)
    private val dao = AppDatabase.create(application).appDao()
    /** Internal mutable state since we expose the ui state as an immutable [StateFlow] */
    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeSelectedApps().collectLatest { apps ->
                _uiState.value = _uiState.value.copy(selectedPackages = apps.map { it.packageName }.toSet())
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val summary = if (repository.hasUsageAccess()) withContext(Dispatchers.IO) {
                repository.today(_uiState.value.selectedPackages)
            } else UsageSummary()
            _uiState.value = _uiState.value.copy(
                summary = summary,
                isLoading = false
            )
        }
    }
}