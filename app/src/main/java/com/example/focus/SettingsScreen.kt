package com.example.focus

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.focus.permissions.RequiredPermissionBanners

@Composable
fun SettingsScreen(navController: NavHostController) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        RequiredPermissionBanners()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("settings/apps") }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Distracting app list")
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Navigate to distracting app selection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // TODO: Selected count here?
        }
        Text("TODO: Global countdown and notification settings")
    }
}

