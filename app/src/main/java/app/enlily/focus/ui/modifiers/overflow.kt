package app.enlily.focus.ui.modifiers

import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Allow a composable to overflow its parent horizontally by [amount] on each side. Note that this must come before a
 * scroll modifier on the child, or else the scroll will clip it.
 */
@SuppressLint("UnnecessaryComposedModifier") // the lint is wrong for some reason
fun Modifier.horizontalOverflow(amount: Dp) = composed {
    val density = LocalDensity.current
    val amountPx = with(density) { amount.roundToPx() }

    layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = constraints.minWidth + amountPx * 2,
                maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
                    Constraints.Infinity
                } else {
                    constraints.maxWidth + amountPx * 2
                }
            )
        )

        layout(
            // if unconstrained, use the placeable's width
            width = if (constraints.maxWidth == Constraints.Infinity) {
                placeable.width - amountPx * 2
            } else {
                constraints.maxWidth
            },
            height = placeable.height
        ) {
            placeable.placeRelative(-amountPx, 0)
        }
    }
}