package com.example.focus.settings

import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A sort of maybe drop-in replacement for [ModalBottomSheet] that can be used in an overlay context.
 * The original attempts to create a window and will fail with
 * `Couldn't add view: DecorView WindowManager: BadTokenException: Unable to add window -- token BinderProxy is not for an application`
 * if created. This simply draws the sheet at the bottom of the screen. Tries to replicate the original drag behavior
 * as closely as possible.
 * One important difference is that it shouldn't be conditionally rendered since it depends on the [sheetState] and won't update it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = {
        BottomSheetDefaults.DragHandle()
    },
    content: @Composable ColumnScope.() -> Unit,
) {
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    val offset = remember { Animatable(0f) }

    fun expandedOffset(): Float = -(sheetHeight - containerHeight).coerceAtLeast(0f)
    fun partiallyExpandedOffset(): Float = (sheetHeight - containerHeight / 2f).coerceIn(expandedOffset(), sheetHeight)
    fun hiddenOffset(): Float = sheetHeight

    /*
     * The sheet is laid out at the bottom of the screen.
     *
     * Expanded: move it upward until its top reaches the top of the window.
     * PartiallyExpanded: put its top around the middle of the window.
     * Hidden: move it completely below the window.
     */
    fun offsetFor(value: SheetValue): Float = when (value) {
        SheetValue.Expanded -> expandedOffset()
        SheetValue.PartiallyExpanded ->
            if (sheetState.hasPartiallyExpandedState) partiallyExpandedOffset() else expandedOffset()
        SheetValue.Hidden -> hiddenOffset()
    }

    /*
     * Keep our visual sheet synchronized with the real Material3 SheetState.
     * This is what makes existing code such as `scope.launch { sheetState.show() }` continue to work.
     */
    LaunchedEffect(
        containerHeight,
        sheetHeight,
        sheetState.targetValue
    ) {
        if (containerHeight <= 0f || sheetHeight <= 0f) return@LaunchedEffect

        val target = offsetFor(sheetState.targetValue)
        if (!initialized) {
            offset.snapTo(target)
            initialized = true
        } else {
            offset.animateTo(
                target,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }
    }

    val expanded = expandedOffset()
    val hidden = hiddenOffset()
    val range = (hidden - expanded).coerceAtLeast(1f)
    val progress = ((hidden - offset.value) / range).coerceIn(0f, 1f)
    val open = progress > 0f

    val view = LocalView.current
    DisposableEffect(view, open) {
        if (!open) return@DisposableEffect onDispose { }

        val dispatcher = view.findOnBackInvokedDispatcher()
        val callback = OnBackInvokedCallback { onDismissRequest() }
        dispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
        onDispose { dispatcher?.unregisterOnBackInvokedCallback(callback) }
    }

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { containerHeight = it.height.toFloat() }
    ) {
        val scope = rememberCoroutineScope()

        if (open) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy( alpha = 0.32f * progress ))
                .pointerInput(Unit) { detectVerticalDragGestures(
                    onVerticalDrag = { _, _ -> },
                    // launch in a coroutine to stay consistent, probably doesn't matter though
                    onDragEnd = { onDismissRequest() }
                ) }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onDismissRequest()
                }
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight()
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .offset { IntOffset(x = 0, y = offset.value.roundToInt()) }
                .pointerInput(sheetHeight, containerHeight) {
                    var dragDirection = 0f

                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()

                            dragDirection = dragAmount

                            val minOffset = expandedOffset()
                            val maxOffset = hiddenOffset()
                            val newOffset = (offset.value + dragAmount).coerceIn(minOffset, maxOffset)
                            scope.launch { offset.snapTo(newOffset) }
                        },

                        onDragEnd = {
                            scope.launch {
                                val expandedAnchor = expandedOffset()
                                val partialAnchor = if (sheetState.hasPartiallyExpandedState) partiallyExpandedOffset() else expandedAnchor
                                val hiddenAnchor = hiddenOffset()

                                // Work out which anchor we should settle on
                                val anchors = listOf(
                                    SheetValue.Expanded to expandedAnchor,
                                    SheetValue.PartiallyExpanded to partialAnchor,
                                    SheetValue.Hidden to hiddenAnchor
                                ).filter { it.first != SheetValue.PartiallyExpanded || sheetState.hasPartiallyExpandedState }

                                val target = anchors.minByOrNull {
                                    val distance = kotlin.math.abs(offset.value - it.second)
                                    val directionalBias = when {
                                        dragDirection > 0f && it.first == SheetValue.Hidden -> -sheetHeight * 0.15f
                                        dragDirection < 0f && it.first == SheetValue.Expanded -> -sheetHeight * 0.15f
                                        else -> 0f
                                    }

                                    distance + directionalBias
                                } ?: (SheetValue.Expanded to expandedAnchor)

                                offset.animateTo(
                                    target.second,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                )

                                when (target.first) {
                                    SheetValue.Hidden -> {
                                        // Keep the real SheetState in sync
                                        // We don't wait for its own animation because the visual animation above already happened
                                        launch { runCatching { sheetState.hide() } }
                                        onDismissRequest()
                                    }
                                    SheetValue.Expanded -> launch { runCatching { sheetState.expand() } }
                                    SheetValue.PartiallyExpanded -> launch { runCatching { sheetState.partialExpand() } }
                                }
                            }
                        },

                        onDragCancel = {
                            scope.launch {
                                val target = offsetFor(sheetState.targetValue)

                                offset.animateTo(
                                    target,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                dragHandle?.invoke()
                content()
            }
        }
    }
}