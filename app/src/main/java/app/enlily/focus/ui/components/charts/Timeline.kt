package app.enlily.focus.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.enlily.focus.PAGE_PADDING
import app.enlily.focus.ui.components.charts.ChartUtils.Companion.endScrollState
import app.enlily.focus.ui.modifiers.horizontalOverflow
import java.time.LocalDate

private fun drawDashedLine(
    drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    dashLength: Float = 10f,
    gapLength: Float = 5f
) {
    var phase = 0f

    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]

        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), phase)
        drawScope.drawLine(color, start, end, strokeWidth, pathEffect = pathEffect)

        val segmentLength = (end - start).getDistance()
        phase += segmentLength
    }
}

/**
 * A Github-style timeline where each column is a week and each row is a day of the week, with the color intensity
 * mapped to the value of the corresponding day. The timeline is scrollable horizontally if it doesn't fit on screen.
 * @param title The title of the timeline
 * @param values The values for each day, in chronological order
 * @param color The color to use for the timeline, with the intensity mapped to the value
 * @param startDate The date of the first value
 * @param formatValue A function to format the value for display
 */
@Composable
fun Timeline(
    title: String,
    values: List<Long>,
    color: Color,
    startDate: LocalDate,
    formatValue: (Long) -> String = { it.toString() }
) {
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val startDayOfWeek = startDate.dayOfWeek.value

    val density = LocalDensity.current
    val boxSize = 18.dp
    val boxSpacing = 3.dp
    val topTextPadding = 16.dp
    val totalHeight = boxSize * 7 + boxSpacing * 6 + topTextPadding
    val totalWidth = (boxSize + boxSpacing) * Math.ceilDiv(values.size + startDayOfWeek - 1, 7) - boxSpacing

    val boxSizePx = with(density) { boxSize.toPx() }
    val boxSpacingPx = with(density) { boxSpacing.toPx() }
    val topTextPaddingPx = with(density) { topTextPadding.toPx() }
    val cornerRadiusPx = with(density) { ChartUtils.CHART_BORDER_RADIUS.toPx() }
    val totalHeightPx = with(density) { totalHeight.toPx() }

    // labels
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val textMeasurer = rememberTextMeasurer()
    val monthTextStyle = MaterialTheme.typography.labelSmall
    val monthTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lightLineColor = color.copy(alpha = .5f)

    fun getColorForValue(value: Long): Color {
        val alpha = (.15f + .85f * value / max)
        return color.copy(alpha = alpha)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Box(Modifier.fillMaxWidth().horizontalOverflow(PAGE_PADDING)) {
            Box(Modifier.fillMaxWidth().horizontalScroll(endScrollState())) {
                Canvas(Modifier.padding(horizontal = PAGE_PADDING).width(totalWidth).height(totalHeight)) {
                    values.forEachIndexed { index, value ->
                        val week = (index + startDayOfWeek - 1) / 7
                        val dayOfWeek = (index + startDayOfWeek - 1) % 7
                        val x = week * (boxSizePx + boxSpacingPx)
                        val y = dayOfWeek * (boxSizePx + boxSpacingPx) + topTextPaddingPx
                        drawRoundRect(
                            color = getColorForValue(value),
                            topLeft = Offset(x, y),
                            size = Size(boxSizePx, boxSizePx),
                            cornerRadius = CornerRadius(cornerRadiusPx)
                        )
                    }

                    // month labels and lines
                    // we place a label after the column of the last day of the previous month, then draw a dashed
                    // separator line (that may need to cross over to the left)
                    val months = values.indices
                        .map { startDate.plusDays(it.toLong()) }
                        .map { it.month to it.year }
                        .distinct()
                    months.forEachIndexed { index, (month, year) ->
                        // find the column of the last day of the previous month
                        val lastDayOfPrevMonth = startDate.plusMonths(index.toLong()).minusDays(
                            startDate.plusMonths(index.toLong()).dayOfMonth.toLong()
                        )
                        val lastDayIndex = values.indices.firstOrNull { startDate.plusDays(it.toLong()) == lastDayOfPrevMonth } ?: return@forEachIndexed
                        val week = Math.ceilDiv(lastDayIndex + startDayOfWeek - 1, 7)

                        val x = week * (boxSizePx + boxSpacingPx) - boxSpacingPx / 2f
                        val monthLabel = month.name.substring(0, 3).lowercase().replaceFirstChar(Char::uppercase)

                        // draw a label at the top
                        val textLayoutResult = textMeasurer.measure(monthLabel, style = monthTextStyle)
                        val textX = x - textLayoutResult.size.width / 2f
                        drawText(textLayoutResult, topLeft = Offset(textX, 0f), color = monthTextColor)

                        // draw a dashed line separating the two months
                        // if the last day index isn't the last day of the week, the line needs to be split into three segments
                        if (lastDayIndex % 7 != 6) {
                            val prevWeekX = (week - 1) * (boxSizePx + boxSpacingPx) - boxSpacingPx / 2f
                            val dayStartY = (lastDayIndex % 7 + 1) * (boxSizePx + boxSpacingPx) - boxSpacingPx / 2f + topTextPaddingPx
                            drawDashedLine(this, listOf(
                                Offset(x, topTextPaddingPx),
                                Offset(x, dayStartY),
                                Offset(prevWeekX, dayStartY),
                                Offset(prevWeekX, totalHeightPx)
                            ), lightLineColor, 2f)
                        } else {
                            drawDashedLine(this, listOf(
                                Offset(x, topTextPaddingPx),
                                Offset(x, totalHeightPx)
                            ), lightLineColor, 2f)
                        }
                    }
                }
                // day labels
            }
            Column(
                Modifier
                    .height(totalHeight - topTextPadding).align(Alignment.BottomStart)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .5f)),
                verticalArrangement = Arrangement.spacedBy(boxSpacing)
            ) {
                days.forEach { day -> Box(Modifier.size(boxSize), contentAlignment = Alignment.Center) {
                    Text(day, style = MaterialTheme.typography.labelMedium)
                } }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("0", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val squares = 5
                repeat(squares) { index ->
                    ChartUtils.KeySquare(getColorForValue(((index + 1) * max / squares)))
                }
            }
            Text(formatValue(max), style = MaterialTheme.typography.labelSmall)
        }
    }
}
