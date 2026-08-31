package app.enlily.focus.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.enlily.focus.usage.UsageViewModel

@Composable
fun DebugSettingsScreen(
    navController: NavHostController,
    viewModel: UsageViewModel
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Debug") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )
        Button(onClick = viewModel::regenerateHistoryManually) {
            Text("Regenerate usage stats")
        }
    }
}