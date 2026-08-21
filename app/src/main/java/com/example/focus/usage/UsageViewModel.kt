package com.example.focus.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.local.AppUsageTotal
import com.example.focus.data.local.DailyUsageCompleteness
import com.example.focus.data.local.DailyUsageStatusEntity
import com.example.focus.data.local.DailyUsageTotals
import com.example.focus.data.local.FocusSessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** The daily app usage totals for the past year, padded with empty days if we don't have data for all of them */
    val dailyTotals: List<DailyUsageTotals> = emptyList(),
    /** The daily focus session times for the past year */
    val dailyFocusSessions: List<FocusSessionSummary> = emptyList(),
    /** The top apps for today, sorted by foreground time */
    val todayApps: List<AppUsageTotal> = emptyList(),
    /** The top apps for the past week, sorted by foreground time */
    val weekApps: List<AppUsageTotal> = emptyList()
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
            loadCharts()
        }
    }

    /** Load data for the charts on the home screen */
    private suspend fun loadCharts() {
        val today = LocalDate.now(ZoneId.systemDefault())
        // 1 year to the nearest monday
        val totalDays = ((today.dayOfYear + 7 - (today.dayOfWeek.value - 1)) % 7 + 365).toLong()
        val yearAgo = today.minusDays(totalDays)
        val weekAgo = today.minusDays(7)
        val daily = dao.usageTotals(yearAgo.toString(), today.toString())
        _uiState.value = _uiState.value.copy(
            // pad to the total length even if we don't have data from all days
            dailyTotals = (daily.size until totalDays).reversed().map { DailyUsageTotals(
                today.minusDays(it).toString(),
                0, 0
            ) } + daily,
            dailyFocusSessions = dao.focusSummaries(yearAgo.plusDays(1).toString(), today.toString()),
            todayApps = dao.topApps(today.toString(), today.toString(), 1000L * 60L * 5L),
            weekApps = dao.topApps(weekAgo.toString(), today.toString(), 1000L * 60L * 5L)
        )
    }

    private fun refreshHistoryIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now(ZoneId.systemDefault())
            val since = today.minusDays(UsageStatsRepository.HISTORY_DAYS - 1L)
            val statuses = dao.dailyUsageStatuses(since.toString()).associateBy { it.date }

            // update all days in the past HISTORY_DAYS that don't have a status
            val days = (0 until UsageStatsRepository.HISTORY_DAYS).map { today.minusDays(it.toLong()) }
                .filter { date ->
                    statuses[date.toString()].let { it == null || it.completeness == DailyUsageCompleteness.PARTIAL }
                }
            if (days.isNotEmpty()) updateDays(days)
        }
    }

    fun regenerateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now(ZoneId.systemDefault())
            updateDays((0 until UsageStatsRepository.HISTORY_DAYS).map { today.minusDays(it.toLong()) })
        }
    }

    private suspend fun updateDays(days: List<LocalDate>) {
        days.forEachIndexed { index, date ->
            dao.clearDailyUsage(date.toString())
            val entries = repository.usageForDate(date)
            entries.takeIf { it.isNotEmpty() }?.let {
                dao.insertDailyUsage(entries)
            }

            // update the day's status to indicate if we have full or partial data for it
            // if it's today, we only have partial data, but for any other day we should have full data
            dao.upsertDailyUsageStatus(DailyUsageStatusEntity(
                date.toString(),
                if (date == LocalDate.now(ZoneId.systemDefault())) DailyUsageCompleteness.PARTIAL else DailyUsageCompleteness.FULL,
                System.currentTimeMillis()
            ))
        }
    }
}