package com.example.focus.accessibility

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus.BuildConfig
import com.example.focus.settings.OverlayModalBottomSheet
import com.example.focus.ui.components.FocusCountdown
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusCheckOverlay(
    appLabel: String,
    usageMillis: Long,
    countdownSeconds: Int,
    onClose: () -> Unit,
    onContinue: (Long) -> Unit
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

    val allowanceSheetState = rememberModalBottomSheetState()
    val allowanceOptions = listOf(1, 5, 10, 15, 20, 30, 45, 60, 90)

    val scope = rememberCoroutineScope()

    val view = LocalView.current
    DisposableEffect(view) {
        val dispatcher = view.findOnBackInvokedDispatcher()
        val callback = OnBackInvokedCallback { onClose() }
        dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
    }

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
                        if (secondsRemaining <= 0) scope.launch { allowanceSheetState.show() }
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
        OverlayModalBottomSheet(
            onDismissRequest = { scope.launch { allowanceSheetState.hide() } },
            sheetState = allowanceSheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    "How much time do you need?",
                    Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                // For testing
                if (BuildConfig.DEBUG) TextButton(
                    onClick = { onContinue(5_000L) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("5 seconds (testing)")
                }
                allowanceOptions.forEach { minutes ->
                    TextButton(
                        onClick = { onContinue(minutes.toLong() * 60_000L) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$minutes minutes")
                    }
                }
            }
        }
    }
}

@Composable
fun FocusBlockingOverlay(
    appLabel: String,
    startedAtMillis: Long,
    durationMillis: Long,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                modifier = Modifier.weight(1f)
            ) {
                Text("$appLabel is unavailable while focusing!", fontSize = 24.sp, textAlign = TextAlign.Center)
                FocusCountdown(startedAtMillis, durationMillis)
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Close app")
            }
        }
    }
}