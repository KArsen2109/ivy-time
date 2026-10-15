package com.ivy.design.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Slice(val value: Float, val color: Color)

/** Donut chart, the shape Ivy Wallet uses for its pie-chart screen. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thickness: Dp = 28.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = Stroke(width = thickness.toPx())
            val inset = thickness.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            if (total <= 0f) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                return@Canvas
            }
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    // A hairline gap keeps neighbouring slices apart.
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        center()
    }
}

/** Columns with a label underneath, for "time per day of the week". */
@Composable
fun BarChart(
    bars: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 120.dp,
) {
    val max = bars.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height * (value / max).coerceIn(0.015f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (value > 0f) barColor else MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Flat progress bar used by goals; Ivy Wallet calls this the budget battery. */
@Composable
fun IvyProgressBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, size = 12.dp)
        Box(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
