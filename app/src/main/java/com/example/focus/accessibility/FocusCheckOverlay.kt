package com.example.focus.accessibility

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    return when {
        totalMinutes < 1 -> "less than a minute"
        totalMinutes == 1L -> "1 minute"
        totalMinutes < 60 -> "$totalMinutes minutes"
        else -> {
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            if (minutes == 0L) "$hours hours" else "$hours h $minutes min"
        }
    }
}

private fun ease(value: Float): Float {
    // Ease in-out quadratic
    return if (value < 0.5f) 2 * value * value else -1 + (4 - 2 * value) * value
}

@Composable
fun FocusCheckOverlay(
    appLabel: String,
    usageMillis: Long,
    countdownSeconds: Int,
    onClose: () -> Unit,
    onContinue: () -> Unit
) {
    val progress = remember(countdownSeconds) { Animatable(0f) }
    LaunchedEffect(countdownSeconds) {
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = tween(
            durationMillis = countdownSeconds * 1_000,
            easing = LinearEasing
        ))
    }
    val secondsRemaining = ceil((1f - progress.value) * countdownSeconds).toInt()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text("focus check!", fontSize = 24.sp)
                Text(
                    "You've used $appLabel for ${formatDuration(usageMillis)} today",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
                )
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (secondsRemaining <= 0) onContinue()
                    },
                    enabled = secondsRemaining <= 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = if(secondsRemaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Filled portion
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ease(progress.value))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if(secondsRemaining > 0) "Continue ($secondsRemaining)" else "Continue")
                        }
                    }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Close app")
                }
            }
        }
    }
}