package app.enlily.focus.ui.components.charts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

class ChartUtils {
    companion object {
        val CHART_BORDER_RADIUS = 3.dp

        @Composable
        fun endScrollState(): ScrollState {
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState) {
                scrollState.scrollTo(snapshotFlow { scrollState.maxValue }.first { it > 0 })
            }
            return scrollState
        }

        fun formatShortDuration(millis: Long): String {
            val seconds = millis / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }

        @Composable
        fun KeySquare(color: Color) {
            Box(Modifier.size(14.dp).background(color, shape = RoundedCornerShape(CHART_BORDER_RADIUS)))
        }
    }
}