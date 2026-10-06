package app.enlily.focus.ui.components.charts

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.enlily.focus.data.local.AppUsageTotal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppDetails(val label: String, val icon: android.graphics.drawable.Drawable?)

/**
 * A horizontal bar chart showing the values of app labels, with the color intensity mapped to the value.
 */
@Composable
@Suppress("FunctionName")
fun AppBarChart(title: String, values: List<AppUsageTotal>) {
    val max = values.maxOfOrNull { it.foregroundMillis }?.coerceAtLeast(1L) ?: 1L

    val rowHeight = 18.dp
    val rowSpacing = 4.dp

    val context = LocalContext.current

    // produceState runs at the start and re-runs whenever key1 changes
    val appDetails by produceState<Map<String, AppDetails>>(emptyMap(), values) {
        value = withContext(Dispatchers.IO) {
            values.associate { usage ->
                val details = try {
                    val appInfo = context.packageManager.getApplicationInfo(usage.packageName, 0)
                    AppDetails(
                        label = context.packageManager.getApplicationLabel(appInfo).toString(),
                        icon = appInfo.loadIcon(context.packageManager)
                    )
                } catch (_: Exception) {
                    AppDetails(label = usage.packageName.substringAfterLast('.'), icon = null)
                }
                usage.packageName to details
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // labels
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                values.forEach { v ->
                    val details = appDetails[v.packageName]
                    Row(
                        Modifier.height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AndroidView(
                            factory = { ImageView(it) },
                            modifier = Modifier.size(rowHeight),
                            update = { imageView ->
                                imageView.setImageDrawable(details?.icon)
                            }
                        )
                        Text(
                            details?.label ?: v.packageName.substringAfterLast('.'),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // bars
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                values.forEach { v ->
                    val color = if (v.isDistracting) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    val onBarColor = if (v.isDistracting) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    val fraction = v.foregroundMillis / max.toFloat()
                    val label = ChartUtils.formatShortDuration(v.foregroundMillis)

                    // keep the label inside the filled portion for bars that are more than half filled
                    if (fraction < .5f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.fillMaxWidth(fraction).height(rowHeight)
                                .clip(RoundedCornerShape(ChartUtils.CHART_BORDER_RADIUS))
                                .background(color.copy(alpha = .2f + .8f * fraction)))
                            Text(label, Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxWidth()
                            .height(rowHeight)
                            .clip(RoundedCornerShape(ChartUtils.CHART_BORDER_RADIUS))
                            .background(color.copy(alpha = .2f + .8f * fraction))
                        ) {
                                Text(label, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                                    color = onBarColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                    }
                }
            }
        }
    }
}
