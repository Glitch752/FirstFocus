package com.example.focus.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

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
        
        Button(
            onClick = { create.launch("focus-backup.zip") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export data") }
        Button(
            onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Import data") }

        confirm?.let { uri ->
            Text("Importing will replace the current database and settings.")
            Button(onClick = { viewModel.import(uri); }, modifier = Modifier.fillMaxWidth()) { Text("Confirm replacement") }
        }
    }
}
