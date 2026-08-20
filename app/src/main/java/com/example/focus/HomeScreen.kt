package com.example.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.focus.permissions.PermissionViewModel
import com.example.focus.permissions.RequiredPermissionBanners
import com.example.focus.usage.UsageViewModel
import com.example.focus.usage.UsageStatsRepository

@Composable
fun HomeScreen(
    viewModel: UsageViewModel = viewModel(),
    permissionViewModel: PermissionViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Focus", style = MaterialTheme.typography.headlineLarge)

        if (!permissionState.isChecking && !permissionState.allGranted) {
            RequiredPermissionBanners(viewModel = permissionViewModel)
        } else if (state.isLoading) {
            Box(Modifier.fillMaxWidth()) { CircularProgressIndicator() }
        }

        if (state.isRegeneratingHistory) {
            Text("Updating usage history...")
            LinearProgressIndicator(
                progress = state.historyProgress / UsageStatsRepository.HISTORY_DAYS.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // TODO: More detailed usage stats, including per app, either here or on click
        SummaryRow("Phone usage today", formatDuration(state.summary.totalMillis))
        SummaryRow("Distracting apps", formatDuration(state.summary.distractingMillis))
        SummaryRow("Focus time today", "todo")
        SummaryRow("Daily target", "todo ")
        
        Text("Statistics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))

        val historyDays = state.history.map { it.date }.distinct().size
        Text("${state.history.size} records across $historyDays days")
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
