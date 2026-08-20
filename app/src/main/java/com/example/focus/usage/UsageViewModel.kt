package com.example.focus.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.local.DailyUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import com.example.focus.data.settings.focusDataStore
import com.example.focus.data.settings.SettingsKeys
import java.time.LocalDate
import java.time.ZoneId

/** State for app usage data */
data class UsageUiState(
    /** The latest summary of data from the usage stats service */
    val summary: UsageSummary = UsageSummary(),
    /** The set of package names that are selected as distracting */
    val selectedPackages: Set<String> = emptySet(),
    /** Whether the summary data is being loaded */
    val isLoading: Boolean = false,

    val history: List<DailyUsageEntity> = emptyList(),
    val isRegeneratingHistory: Boolean = false,
    val historyProgress: Int = 0,
    val historyDaysLoaded: Int = 0
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsageStatsRepository(application)
    private val dao = AppDatabase.create(application).appDao()
    /** Internal mutable state since we expose the ui state as an immutable [StateFlow] */
    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState.asStateFlow()

    init {
        UsageRefreshScheduler(application).scheduleNext()
        viewModelScope.launch {
            val since = LocalDate.now(ZoneId.systemDefault()).minusDays(30).toString()
            dao.observeDailyUsage(since).collectLatest { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
        viewModelScope.launch {
            dao.observeSelectedApps().collectLatest { apps ->
                _uiState.value = _uiState.value.copy(selectedPackages = apps.map { it.packageName }.toSet())
                refreshHistoryIfNeeded()
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

    private fun refreshHistoryIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val preferences = getApplication<Application>().focusDataStore.data.first()
            val lastUpdated = preferences[SettingsKeys.usageHistoryLastUpdated] ?: 0L
            val now = System.currentTimeMillis()
            // 12 hours is super arbitrary, i don't know if we even need to
            // run a full update but it could find inconsistencies i think
            if (now - lastUpdated < 12 * 60 * 60_000L) return@launch
            updateDays(listOf(LocalDate.now(ZoneId.systemDefault())))
            getApplication<Application>().focusDataStore.edit {
                it[SettingsKeys.usageHistoryLastUpdated] = now
            }
        }
    }

    private suspend fun updateDays(days: List<LocalDate>) {
        _uiState.value = _uiState.value.copy(isRegeneratingHistory = true, historyProgress = 0)
        days.forEachIndexed { index, date ->
            dao.clearDailyUsage(date.toString())
            repository.usageForDate(date).takeIf { it.isNotEmpty() }?.let { entries ->
                dao.insertDailyUsage(entries)
            }
            _uiState.value = _uiState.value.copy(historyProgress = index + 1, historyDaysLoaded = days.size)
        }
        _uiState.value = _uiState.value.copy(isRegeneratingHistory = false)
    }
}