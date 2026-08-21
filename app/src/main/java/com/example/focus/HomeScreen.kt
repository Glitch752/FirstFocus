package com.example.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focus.permissions.PermissionViewModel
import com.example.focus.permissions.RequiredPermissionBanners
import com.example.focus.ui.components.charts.AppBarChart
import com.example.focus.ui.components.charts.DailyChart
import com.example.focus.ui.components.charts.Timeline
import com.example.focus.usage.UsageViewModel
import java.time.LocalDate

val PAGE_PADDING = 18.dp

@Composable
fun HomeScreen(
    viewModel: UsageViewModel = viewModel(),
    permissionViewModel: PermissionViewModel = viewModel()
) {
    val usageState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PAGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Focus", style = MaterialTheme.typography.headlineLarge)

        if (!permissionState.isChecking && !permissionState.allGranted) {
            RequiredPermissionBanners(viewModel = permissionViewModel)
        } else if (usageState.isLoading) {
            Box(Modifier.fillMaxWidth()) { CircularProgressIndicator() }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SummaryRow("Phone usage today", formatDuration(usageState.summary.totalMillis))
            SummaryRow("Distracting apps", formatDuration(usageState.summary.distractingMillis))
            SummaryRow("Focus time today", formatDuration(usageState.dailyFocusSessions.lastOrNull()?.finishedMillis ?: 0L))
        }

        // todo: some sort of "shame statistic", like:
        // - "you spent 2 hours more than your average on distracting apps today"
        // - "you spent 30% of your day on Youtube today"
        // stuff like that

        if (usageState.dailyTotals.isEmpty()) {
            Text("Loading usage data...", style = MaterialTheme.typography.bodyMedium)
            CircularProgressIndicator(Modifier.padding(8.dp))
        } else {
            // TODO: more data analysis on click of many of these charts

            val usageStartDay = LocalDate.parse(usageState.dailyTotals[0].date)
            Timeline(
                "Total phone usage",
                usageState.dailyTotals.map { it.totalForegroundMillis },
                MaterialTheme.colorScheme.primary,
                usageStartDay,
                formatValue = { formatDuration(it) }
            )
            Timeline(
                "Distracting app usage",
                usageState.dailyTotals.map { it.distractingForegroundMillis },
                MaterialTheme.colorScheme.secondary,
                usageStartDay,
                formatValue = { formatDuration(it) }
            )
            val focusStartDay = LocalDate.parse(usageState.dailyFocusSessions[0].date)
            Timeline(
                "Focus session time",
                usageState.dailyFocusSessions.map { it.finishedMillis },
                MaterialTheme.colorScheme.inverseSurface,
                focusStartDay,
                formatValue = { formatDuration(it) }
            )

            DailyChart(usageState.dailyTotals, usageState.dailyFocusSessions)

            // TODO: hourly usage charts?

            AppBarChart("Apps used today", usageState.todayApps)
            AppBarChart("Apps used this week", usageState.weekApps)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${minutes % 60}m"
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}
