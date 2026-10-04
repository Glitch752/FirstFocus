package app.enlily.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enlily.focus.permissions.PermissionViewModel
import app.enlily.focus.permissions.RequiredPermissionBanners
import app.enlily.focus.ui.components.charts.AppBarChart
import app.enlily.focus.ui.components.charts.DailyChart
import app.enlily.focus.ui.components.charts.Timeline
import app.enlily.focus.usage.UsageViewModel
import java.time.LocalDate

val PAGE_PADDING = 18.dp

// TODO: pull to refresh

// todo: some sort of "shame statistic", like:
// - "you spent 2 hours more than your average on distracting apps today"
// - "you spent 30% of your day on Youtube today"
// stuff like that

@Composable
@Suppress("FunctionName")
fun HomeScreen(
    viewModel: UsageViewModel = viewModel(),
    permissionViewModel: PermissionViewModel = viewModel()
) {
    val usageState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    
    // cache everything. not sure if all of these are required (since a lot of this data will change frequently), but it doens't hurt
    val usageStartDay = remember(usageState.dailyTotals) { usageState.dailyTotals.firstOrNull()?.date?.let(LocalDate::parse) ?: LocalDate.now() }
    val usageValues = remember(usageState.dailyTotals) { usageState.dailyTotals.map { it.totalForegroundMillis } }
    val distractingValues = remember(usageState.dailyTotals) { usageState.dailyTotals.map { it.distractingForegroundMillis } }    
    val focusStartDay = remember(usageState.dailyFocusSessions, usageStartDay) { usageState.dailyFocusSessions.firstOrNull()?.date?.let(LocalDate::parse) ?: usageStartDay }
    val focusValues = remember(usageState.dailyFocusSessions) { usageState.dailyFocusSessions.map { it.finishedMillis } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = PAGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = PAGE_PADDING)
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Focus", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    viewModel.refresh()
                    permissionViewModel.refresh()
                }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }

        if (!permissionState.isChecking && !permissionState.allGranted) {
            item(key = "permissions") {
                RequiredPermissionBanners(viewModel = permissionViewModel)
            }
        } else if (usageState.isLoading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth()) { CircularProgressIndicator() }
            }
        }

        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SummaryRow("Phone usage today", formatDuration(usageState.summary.totalMillis))
                SummaryRow("Distracting apps", formatDuration(usageState.summary.distractingMillis))
                SummaryRow("Focus time today", formatDuration(usageState.dailyFocusSessions.lastOrNull()?.finishedMillis ?: 0L))
            }
        }

        if (usageState.dailyTotals.isEmpty()) {
            item(key = "empty-state") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Loading usage data...", style = MaterialTheme.typography.bodyMedium)
                    CircularProgressIndicator(Modifier.padding(8.dp))
                }
            }
        } else {
            item(key = "total-usage-timeline") {
                Timeline(
                    "Total phone usage",
                    usageValues,
                    MaterialTheme.colorScheme.primary,
                    usageStartDay,
                    formatValue = { formatDuration(it) }
                )
            }
            item(key = "distracting-usage-timeline") {
                Timeline(
                    "Distracting app usage",
                    distractingValues,
                    MaterialTheme.colorScheme.secondary,
                    usageStartDay,
                    formatValue = { formatDuration(it) }
                )
            }
            item(key = "focus-session-timeline") {
                Timeline(
                    "Focus session time",
                    focusValues,
                    MaterialTheme.colorScheme.inverseSurface,
                    focusStartDay,
                    formatValue = { formatDuration(it) }
                )
            }
            item(key = "daily-chart") {
                DailyChart(usageState.dailyTotals, usageState.dailyFocusSessions)
            }
            item(key = "apps-today") {
                AppBarChart("Apps used today", usageState.todayApps)
            }
            item(key = "apps-this-week") {
                AppBarChart("Apps used this week", usageState.weekApps)
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}

@Composable
@Suppress("FunctionName")
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}
