package com.example.focus.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.focus.BuildConfig
import com.example.focus.permissions.RequiredPermissionBanners

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = viewModel()
) {
    val countdownSeconds by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val automaticallyEnd by viewModel.automaticallyEnd.collectAsStateWithLifecycle()
    Column(
        Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        RequiredPermissionBanners()

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("settings/apps") },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distracting app list", Modifier.padding(vertical = 8.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Navigate to distracting app selection",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // TODO: Selected count here?
            }

            Column {
                Text("Focus check countdown: ${countdownSeconds}s")
                Slider(
                    value = countdownSeconds.toFloat(),
                    onValueChange = { viewModel.setCountdownSeconds(it.toInt()) },
                    valueRange = 0f..20f,
                    steps = 20
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutomaticallyEnd(!automaticallyEnd) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Automatically end focus sessions")
                Switch(
                    checked = automaticallyEnd,
                    onCheckedChange = viewModel::setAutomaticallyEnd
                )
            }

            if (BuildConfig.DEBUG) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("settings/debug") },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Debug", Modifier.padding(vertical = 8.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Navigate to debug settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

