package com.example.focus.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.focus.permissions.RequiredPermissionBanners

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = viewModel()
) {
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        RequiredPermissionBanners()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("settings/apps") },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Distracting app list", Modifier.padding(vertical = 8.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate to distracting app selection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // TODO: Selected count here?
        }
        Text("Focus check countdown: ${countdownSeconds}s")
        Slider(
            value = countdownSeconds.toFloat(),
            onValueChange = { viewModel.setCountdownSeconds(it.toInt()) },
            valueRange = 0f..20f,
            steps = 20
        )
    }
}

