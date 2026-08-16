package com.example.focus.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private fun formatDuration(millis: Long): String {
    // format a time as hh:mm:ss or mm:ss depending on the length of the time
    val totalSeconds = millis / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
@Preview
fun FocusCountdown(startedAtMillis: Long = System.currentTimeMillis() - 1 * 60_000L, durationMillis: Long = 25 * 60_000L) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMillis, durationMillis) {
        // update the current time every frame to keep the countdown accurate
        while (true) {
            withFrameNanos { }
            nowMillis = System.currentTimeMillis()
        }
    }
    val remainingMillis = max(0L, startedAtMillis + durationMillis - nowMillis)

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 64.dp).aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            formatDuration(remainingMillis),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        // progress bar
        CircularProgressIndicator(
            progress = { (durationMillis - remainingMillis).toFloat() / durationMillis },
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
