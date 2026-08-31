package app.enlily.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.enlily.focus.focus.FocusSessionViewModel
import app.enlily.focus.ui.components.FocusCountdown
import app.enlily.focus.ui.components.TimeLineSlider

private fun formatMinuteDuration(minutes: Long, alwaysIncludeMinutes: Boolean = true): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) {
        if (alwaysIncludeMinutes || remainingMinutes > 0) {
            "%dh %02dm".format(hours, remainingMinutes)
        } else {
            "%dh".format(hours)
        }
    } else {
        "%dm".format(remainingMinutes)
    }
}

// TODO: suddenly doesn't update after starting/stopping sessions?
// TODO: styled poorly in landscape mode

@Composable
fun FocusSessionScreen(viewModel: FocusSessionViewModel = viewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    var targetMinutes by remember { mutableLongStateOf(25) }

    Text(
        if (active == null) "Focus session" else "Focus session in progress",
        Modifier.padding(18.dp),
        style = MaterialTheme.typography.titleLarge
    )
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (active == null) {
            TimeLineSlider(
                value = targetMinutes,
                onValueChange = { targetMinutes = it },
                color = MaterialTheme.colorScheme.primary,
                range = 1L..600L,
                modifier = Modifier.padding(vertical = 8.dp),
                formatLabel = { formatMinuteDuration(it, alwaysIncludeMinutes = false) }
            ) {
                Text(
                    formatMinuteDuration(targetMinutes),
                    Modifier.fillMaxWidth().padding(18.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            }

            val presetDurations = listOf(15L, 30L, 45L, 60L, 90L, 120L, 180L)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow (
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetDurations.forEach { duration ->
                    val selected = targetMinutes == duration
                    val containerColor by animateColorAsState(if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    })
                    val contentColor by animateColorAsState(if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    })

                    Button(
                        onClick = { targetMinutes = duration },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Text(formatMinuteDuration(duration, alwaysIncludeMinutes = false))
                    }
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            Button(
                onClick = { viewModel.start(targetMinutes * 60_000L) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start focus session") }
        } else {
            FocusCountdown(
                startedAtMillis = active!!.startedAtMillis,
                durationMillis = active!!.plannedDurationMillis
            )

            Spacer(Modifier.size(24.dp))

            Button(
                onClick = viewModel::stop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Stop focus session")
            }
        }
    }
}
