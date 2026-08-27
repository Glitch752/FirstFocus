package com.example.focus.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.example.focus.data.local.FocusReminderEntity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

fun formatTime(hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(calendar.timeInMillis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusReminderEditorScreen(
    navController: NavHostController,
    existing: FocusReminderEntity? = null,
    viewModel: FocusRemindersViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var title by remember(existing) { mutableStateOf(existing?.title ?: "Focus reminder") }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    var hour by remember(existing) { mutableIntStateOf(existing?.hour ?: 9) }
    var minute by remember(existing) { mutableIntStateOf(existing?.minute ?: 0) }
    var daysMask by remember(existing) { mutableIntStateOf(existing?.daysOfWeek ?: 0x7F) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopAppBar(
            title = { Text(if (existing == null) "Add reminder" else "Edit reminder") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                IconButton(onClick = navController::popBackStack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (existing != null) {
                    IconButton(onClick = {
                        viewModel.delete(existing)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        )

        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
        Row(
            Modifier.fillMaxWidth().clickable { enabled = !enabled },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enabled")
            Switch(enabled, { enabled = it }, Modifier.scale(0.8f))
        }

        Button(
            onClick = { showTimePicker = true },
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(
                formatTime(hour, minute),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
        }

        Column {
            Text("Days", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, label ->
                    val bit = 1 shl index
                    val isActive = (daysMask and bit) != 0
                    val containerColor by animateColorAsState(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                    )
                    val contentColor by animateColorAsState(
                        if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = { daysMask = daysMask xor bit },
                        Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        val saveEnabled = daysMask != 0 && title.isNotBlank()
        val saveContainerColor by animateColorAsState(
            if (saveEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        )
        val saveContentColor by animateColorAsState(
            if (saveEnabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                viewModel.upsert(FocusReminderEntity(
                    existing?.id ?: 0L,
                    title.trim().ifEmpty { "Focus reminder" },
                    hour, minute, enabled,
                    daysMask,
                    existing?.durationMillis ?: 0L
                ))
                navController.popBackStack()
            },
            Modifier.fillMaxWidth(),
            enabled = saveEnabled,
            // material doesn't animate between normal and disabled colors by default, so we do it manually
            colors = ButtonDefaults.buttonColors(
                containerColor = saveContainerColor, contentColor = saveContentColor,
                disabledContainerColor = saveContainerColor, disabledContentColor = saveContentColor
            )
        ) {
            Text("Save", style = MaterialTheme.typography.bodyLarge)
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(hour, minute, is24Hour = false)
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showTimePicker = false }) {
                            Text("Cancel")
                        }
                        Button(onClick = { hour = state.hour; minute = state.minute; showTimePicker = false }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
