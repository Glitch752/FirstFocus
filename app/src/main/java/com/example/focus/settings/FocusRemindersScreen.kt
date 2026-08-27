package com.example.focus.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.focus.data.local.FocusReminderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRemindersScreen(navController: NavHostController, viewModel: FocusRemindersViewModel = viewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TopAppBar(
            title = { Text("Focus reminders") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                IconButton(onClick = navController::popBackStack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        Box() {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // we need lots of bottom padding so the user can scroll beyond the button; overscroll feels nice here so it's okay
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(reminders, key = { it.id }) { reminder -> ReminderRow(reminder, viewModel, navController) }
            }

            Button(
                onClick = {
                    navController.navigate("settings/reminders/edit/0")
                },
                Modifier.padding(16.dp).align(Alignment.BottomCenter),
            ) {
                Icon(Icons.Default.Add, "Add reminder")
                Text("Add reminder", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: FocusReminderEntity,
    viewModel: FocusRemindersViewModel,
    navController: NavHostController
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().clickable(onClick = {
        navController.navigate("settings/reminders/edit/${reminder.id}")
    })) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                Text(reminder.title, style = MaterialTheme.typography.titleSmall)
                Text(formatTime(reminder.hour, reminder.minute), style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (reminder.daysOfWeek == 0x7F) "Every day" else reminder.daysOfWeekAsString(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((-12).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    Switch(
                        modifier = Modifier.scale(0.8f),
                        checked = reminder.enabled,
                        onCheckedChange = { viewModel.setEnabled(reminder, it) }
                    )
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, "Delete reminder")
                    }
                }

                IconButton(onClick = {
                    navController.navigate("settings/reminders/edit/${reminder.id}")
                }, Modifier.scale(0.8f)) {
                    Icon(Icons.Default.ChevronRight, "Edit reminder")
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete reminder?") },
            text = { Text("Are you sure you want to delete \"${reminder.title}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.delete(reminder)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
