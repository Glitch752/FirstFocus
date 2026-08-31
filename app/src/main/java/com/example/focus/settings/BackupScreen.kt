package com.example.focus.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Output
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.focus.MainActivity

@Composable
fun BackupScreen(navController: NavHostController, viewModel: BackupViewModel = viewModel()) {
    // Launchers for the export and import activities, handling the file selection and creation dialogs and providing
    // us with a URI to read from or write to.
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { it?.let(viewModel::export) }
    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let(viewModel::requestImport) }

    // when the database is replaced, just recreate everything since we don't want to hold old DAOs accidentally
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.dataReplaced.collect {
            // Activity.recreate() retains the ViewModelStore, so UsageViewModel and other
            // repositories would still hold DAOs from the closed Room instance. Start a fresh
            // task instead, which disposes all existing ViewModels and repositories.
            val activity = context as? Activity ?: return@collect
            val restartIntent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(MainActivity.EXTRA_REGENERATE_HISTORY, true)
                }
            if (restartIntent != null) {
                activity.startActivity(restartIntent)
                activity.finish()
            }
        }
    }

    var showClearConfirmation by remember { mutableStateOf(false) }
    val confirm by viewModel.pendingImport.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Import / export") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { create.launch("focus-backup.zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Output, "Export")
                Spacer(Modifier.size(8.dp))
                Text("Export data")
            }
            Button(
                onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Input, "Import")
                Spacer(Modifier.size(8.dp))
                Text("Import data")
            }

            Button(
                onClick = { showClearConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, "Clear")
                Spacer(Modifier.size(8.dp))
                Text("Clear all application data")
            }

            Card() {
                Text("This will import or export all settings and data to an archive file. " +
                    "Note that we will regenerate the last ~7 days of data on import based on Android's usage stats, " +
                    "so a few days of data may disappear if you're using a different device.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingImport() },
            title = { Text("Replace application data?") },
            text = { Text("The current database and settings will be permanently replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm?.let(viewModel::import)
                    viewModel.clearPendingImport()
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingImport() }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear all application data?") },
            text = { Text("This permanently deletes the database and all settings. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    viewModel.clearAll()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") } }
        )
    }
}
