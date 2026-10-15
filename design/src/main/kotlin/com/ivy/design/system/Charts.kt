package com.ivy.design.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Slice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    thickness: Dp = 18.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val empty = IvyTheme.extra.line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt)
            val inset = thickness.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (total <= 0f) {
                drawArc(empty, 0f, 360f, false, topLeft, arcSize, style = stroke)
                return@Canvas
            }
            var angle = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total)
                drawArc(
                    color = slice.color,
                    startAngle = angle,
                    // A hairline gap keeps neighbouring slices apart without a stroke.
                    sweepAngle = (sweep - 1.2f).coerceAtLeast(0.4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                angle += sweep
            }
        }
        center()
    }
}

/** Columns with a label underneath, for time per day. */
@Composable
fun BarChart(
    bars: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlight: Int = -1,
    height: Dp = 96.dp,
) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
    val line = IvyTheme.extra.line
    val slate = IvyTheme.extra.slate
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height * (value / max).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                when {
                                    value <= 0f -> line
                                    index == highlight -> barColor
                                    else -> barColor.copy(alpha = 0.45f)
                                }
                            )
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == highlight) MaterialTheme.colorScheme.onSurface else slate,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color)
        Box(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The ring behind the running clock. It fills once an hour, so a glance says
 * roughly how long the current stretch has been going without reading the digits.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp,
    thickness: Dp = 7.dp,
    content: @Composable () -> Unit,
) {
    val track = IvyTheme.extra.line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Round)
            val inset = thickness.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}
