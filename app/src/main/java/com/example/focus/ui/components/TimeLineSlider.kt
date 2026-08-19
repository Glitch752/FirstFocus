package com.example.focus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * A draggable, fixed-scale number line.
 * Includes haptic feedback, fling, and snapping.
 */
@Composable
fun TimeLineSlider(
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    range: LongRange = 1L..100L,
    color: Color,
    formatLabel: ((Long) -> String)? = null,
    /** Content that will be included in the gesture scope, like a label showing the value. Displayed below the timeline. */
    content: @Composable () -> Unit = { }
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // I don't fully understand what this does, but I guess it fixes issues with using onValueChange in a lambda?
    val onValueChangeState = rememberUpdatedState(onValueChange)
    /** The last value reported to onValueChange so we know if the value was updated externally */
    var lastReported by remember { mutableLongStateOf(value) }

    /** The continuous position of the slider, in output units, used for drawing */
    var position by remember { mutableFloatStateOf(value.toFloat()) }
    /** A state that always contains the current position, for use in gestures */
    val currentPosition = rememberUpdatedState(position)
    /** An animatable storing the current scroll position, in output units, used while flinging */
    val scroll = remember { Animatable(value.toFloat()) }
    /** Prevent an externally-driven animation from reporting its intermediate values back upstream. */
    var externallyAnimating by remember { mutableStateOf(false) }

    /** A job settling the position (flinging then snapping to the nearest unit) after a drag */
    var settleJob by remember { mutableStateOf<Job?>(null) }
    /** The velocity of the last release, in output units per second */
    var lastReleaseVelocity by remember { mutableFloatStateOf(0f) }

    /** The number of pixels per output unit */
    val pxPerUnit = with(density) { 12.dp.toPx() }

    val scope = rememberCoroutineScope()

    // If the value changes externally, update the position and snap to it
    LaunchedEffect(value) {
        if (value != lastReported) {
            lastReported = value
            settleJob?.cancel()
            settleJob = null

            externallyAnimating = true
            try {
                scroll.animateTo(
                    value.toFloat(),
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            } finally { externallyAnimating = false }
        }
    }

    /** Report a new value and trigger haptic feedback */
    fun reportAndHaptic(v: Float) {
        position = v
        val rounded = v.roundToLong().coerceIn(range.first, range.last)
        if (rounded != lastReported) {
            // If we're animating to a different external value, the parent already owns it and
            // reporting intermediate values would mess up the animation
            if (!externallyAnimating) {
                lastReported = rounded
                onValueChangeState.value(rounded)
            }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // If the scroll animation changes its value, update the position and report the value if it changed
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { v ->
            val clamped = v.coerceIn(range.first.toFloat(), range.last.toFloat())
            reportAndHaptic(clamped)
        }
    }

    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(fontSize = 12.sp, color = color.copy(alpha = 0.6f))
    val minorColor = color.copy(alpha = 0.25f)
    val majorColor = color.copy(alpha = 0.6f)
    val centerColor = color.copy()

    val timelineHeight = 48.dp
    val numberPadding = 16.dp

    Column(
        modifier.fillMaxWidth()
            .pointerInput(range) { awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                settleJob?.cancel()
                settleJob = null

                // position tracking for an active gesture
                var gesturePosition = currentPosition.value
                // track event positions to calculate velocity
                var lastX = down.position.x
                var lastTime = down.uptimeMillis
                val samples = ArrayDeque<Pair<Long, Float>>()
                samples.addLast(down.uptimeMillis to down.position.x)

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val now = change.uptimeMillis
                    val x = change.position.x
                    val dt = now - lastTime

                    if (dt > 0L) { // probably always true
                        val dx = x - lastX
                        if (dx != 0f) {
                            // Ignore duplicate pointer events and keep a short history so final low-speed events
                            // don't break the flick velocity. I'm not exactly sure why they get duplicated like that?
                            samples.addLast(now to x)
                            while (samples.first().first < now - 100L) samples.removeFirst()
                            // update the position
                            gesturePosition = (gesturePosition - dx / pxPerUnit).coerceIn(range.first.toFloat(), range.last.toFloat())
                            reportAndHaptic(gesturePosition)
                        }
                        lastX = x
                        lastTime = now
                        if (!change.pressed) break
                    }

                    change.consume()
                    if (!change.pressed) break
                }

                // calculate velocity
                val firstSample = samples.firstOrNull()
                val lastSample = samples.lastOrNull()
                val sampleDuration = (lastSample?.first ?: 0L) - (firstSample?.first ?: 0L)
                val velocityPx = if (firstSample != null && lastSample != null && sampleDuration > 0L) {
                    (lastSample.second - firstSample.second) * 1000f / sampleDuration
                } else {
                    0f
                }
                val velocityUnitsPerSecond = -velocityPx / pxPerUnit
                lastReleaseVelocity = velocityUnitsPerSecond

                // launch a job to fling then snap to the nearest unit
                settleJob = scope.launch {
                    scroll.snapTo(gesturePosition)

                    // move and apply friction until the velocity is low enough to snap to the nearest unit
                    var velocity = velocityUnitsPerSecond
                    /** The time of the previous frame, in nanoseconds, for calculating dt */
                    var previousFrame = 0L
                    while (abs(velocity) > 0.5f) { // arbitrary limit, feels nice
                        val frameTime = withFrameNanos { it }
                        if (previousFrame == 0L) {
                            previousFrame = frameTime
                            continue
                        }

                        // 1b nanos = 1s; 1e9f being a number feels silly
                        val dt = ((frameTime - previousFrame) / 1e9f).coerceIn(0.001f, 0.1f)
                        previousFrame = frameTime

                        // update position by velocity
                        val nextPosition = (scroll.value + velocity * dt).coerceIn(range.first.toFloat(), range.last.toFloat())
                        scroll.snapTo(nextPosition)

                        if (nextPosition == range.first.toFloat() && velocity < 0f ||
                            nextPosition == range.last.toFloat() && velocity > 0f) {
                            // stop if the fling reaches a bound
                            velocity = 0f
                        } else {
                            // apply exponential friction; 6f is arbitrary but feels nice
                            velocity *= exp(-6f * dt)
                        }
                    }

                    // snap to the nearest unit
                    val target = scroll.value.roundToLong().coerceIn(range.first, range.last)
                    scroll.animateTo(target.toFloat(), animationSpec = tween(180, easing = FastOutSlowInEasing))
                }
            } }
    ) {
        Canvas(Modifier.fillMaxWidth().height(timelineHeight + numberPadding)) {
            // draw the timeline

            val center = size.width / 2f
            val timelineHeightPx = with(density) { timelineHeight.toPx() }
            val numberPaddingPx = with(density) { numberPadding.toPx() }
            val baselineY = timelineHeightPx / 2f + numberPaddingPx

            // visible window
            val firstVisible = (floor(position - center / pxPerUnit).toInt() - 1).coerceAtLeast(range.first.toInt())
            val lastVisible = (ceil(position + center / pxPerUnit).toInt() + 1).coerceAtMost(range.last.toInt())

            for (value in firstVisible..lastVisible) {
                val x = center + (value - position) * pxPerUnit
                val isMajor = value % 5 == 0
                val isTen = value % 10 == 0

                val distance = abs(x - center) / size.width
                val fade = (1f - distance * 1.6f).coerceIn(0f, 1f)

                val tickColor = when {
                    isTen -> centerColor.copy(alpha = 0.9f * fade)
                    isMajor -> majorColor.copy(alpha = majorColor.alpha * fade)
                    else -> minorColor.copy(alpha = minorColor.alpha * fade)
                }
                val tickExtent = when {
                    isTen -> timelineHeightPx * 0.9f
                    isMajor -> timelineHeightPx * 0.65f
                    else -> timelineHeightPx * 0.35f
                } / 2f

                val strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                drawLine(
                    color = tickColor,
                    start = Offset(x, baselineY - tickExtent), end = Offset(x, baselineY + tickExtent),
                    strokeWidth = strokeWidth, cap = StrokeCap.Round
                )

                if (isTen) {
                    val label = formatLabel?.invoke(value.toLong()) ?: value.toString()
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(x - measured.size.width / 2f, 0f)
                    )
                }
            }

            // center indicator
            val indicatorHeight = timelineHeightPx
            val indicatorY = baselineY - indicatorHeight / 2f - with(density) { 1.dp.toPx() }
            drawLine(
                color = centerColor,
                start = Offset(center, indicatorY),
                end = Offset(center, baselineY + indicatorHeight / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = centerColor,
                radius = 3.dp.toPx(),
                center = Offset(center, indicatorY)
            )

            // val debugText = "release: ${"%.1f".format(lastReleaseVelocity)} min/s"
            // val debugLayout = textMeasurer.measure(debugText, labelStyle)
            // drawText(
            //     textLayoutResult = debugLayout,
            //     color = color.copy(alpha = 0.5f),
            //     topLeft = Offset(4.dp.toPx(), size.height - debugLayout.size.height.toFloat())
            // )
        }
        content()
    }
}
