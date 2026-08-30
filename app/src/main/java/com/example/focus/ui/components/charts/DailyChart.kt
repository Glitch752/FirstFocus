package com.example.focus.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.focus.PAGE_PADDING
import com.example.focus.data.local.DailyUsageTotals
import com.example.focus.data.local.FocusSessionSummary
import com.example.focus.ui.modifiers.horizontalOverflow
import java.time.LocalDate
import kotlin.math.ceil

/**
 * A vertical bar chart displaying daily usage values.
 */
@Composable
fun DailyChart(usageDays: List<DailyUsageTotals>, focusDays: List<FocusSessionSummary>) {
    val combined = usageDays.map { day ->
        val focusDay = focusDays.find { it.date == day.date }
        Pair(day, focusDay)
    }
    // this isn't technically right, but we add focus sessions on top of normal foreground usage.
    // technically, when using non-distracting apps during focus sessions, it will be double-counted, but
    // it's really difficult and computationally expensive to figure out exactly how much of the foreground
    // usage was during focus sessions.
    val max = combined.maxOfOrNull {
        it.first.totalForegroundMillis.toFloat() + (it.second?.finishedMillis?.toFloat() ?: 0f)
    }?.coerceAtLeast(1f) ?: 1f
    val barHeight = 148.dp

    val hourMillis = 60 * 60 * 1000f
    val scaleHours = ceil(max / hourMillis).toInt().coerceAtLeast(1)
    // Keep the grid readable for long durations while keeping every interval an integer number of hours
    val scaleIntervalHours = ceil(scaleHours / 12f).toInt().coerceAtLeast(1)
    val scaleLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    val listState = rememberLazyListState()
    LaunchedEffect(combined.size) {
        if (combined.isNotEmpty()) listState.scrollToItem(index = combined.lastIndex)
    }

    val density = LocalDensity.current
    val barHeightPx = with(density) { barHeight.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val chartLabelStyle = MaterialTheme.typography.labelSmall

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Daily usages", Modifier, style = MaterialTheme.typography.titleLarge)
        // key
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChartUtils.KeySquare(MaterialTheme.colorScheme.secondary)
            Text("Distracting apps", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            ChartUtils.KeySquare(MaterialTheme.colorScheme.primary)
            Text("Non-distracting", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(4.dp))
            ChartUtils.KeySquare(MaterialTheme.colorScheme.inverseSurface)
            Text("Focus sessions", style = MaterialTheme.typography.labelSmall)
        }

        // chart
        Box(Modifier.fillMaxWidth().horizontalOverflow(PAGE_PADDING)) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = PAGE_PADDING)
            ) {
                items(combined.size) { index ->
                    val usageDay = usageDays[index]
                    val total = usageDay.totalForegroundMillis.toFloat()
                    val distracting = usageDay.distractingForegroundMillis.toFloat()
                    val focus = combined[index].second?.finishedMillis?.toFloat() ?: 0f
                    val nonDistracting = total - distracting
                    val alpha = (.15f + .85f * total / max)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(18.dp).padding(horizontal = 2.dp).height(barHeight),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Column(
                                Modifier.clip(RoundedCornerShape(ChartUtils.CHART_BORDER_RADIUS)),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.fillMaxWidth()
                                    .height(barHeight * focus / max)
                                    .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = alpha))
                                )
                                Box(Modifier.fillMaxWidth()
                                    .height(barHeight * nonDistracting / max)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                )
                                Box(Modifier.fillMaxWidth()
                                    .height(barHeight * distracting / max)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha))
                                )
                            }
                        }

                        val day = usageDay.date.substringAfterLast('-').toIntOrNull() ?: 0
                        // short month name IF the month is different from the previous day
                        val month = if (day == 1 || index == 0) {
                            LocalDate.parse(usageDay.date).month.name.substring(0, 3)
                                .lowercase().replaceFirstChar(Char::uppercase)
                        } else null
                        Text(usageDay.date.substringAfterLast('-'), style = MaterialTheme.typography.labelSmall)
                        if (month != null) {
                            Text(month, Modifier.height(16.dp), style = MaterialTheme.typography.labelSmall)
                        } else {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }

            val textBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = .5f)
            val textPaddingPx = with(density) { 32.dp.toPx() }
            val sidePaddingPx = with(density) { 8.dp.toPx() }
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    color = textBackgroundColor,
                    topLeft = Offset(0f, 0f),
                    size = size.copy(width = textPaddingPx - sidePaddingPx / 2)
                )
                for (hour in scaleIntervalHours until scaleHours step scaleIntervalHours) {
                    val y = barHeightPx * (1f - hour * hourMillis / max)
                    drawLine(
                        color = scaleLineColor,
                        start = Offset(textPaddingPx, y),
                        end = Offset(size.width - sidePaddingPx, y),
                        strokeWidth = 1f
                    )

                    val textLayoutResult = textMeasurer.measure(text = "${hour}h", style = chartLabelStyle)
                    drawText(textLayoutResult, topLeft = Offset(
                        textPaddingPx - textLayoutResult.size.width - sidePaddingPx,
                        y - textLayoutResult.size.height / 2
                    ))
                }
            }
        }
    }
}