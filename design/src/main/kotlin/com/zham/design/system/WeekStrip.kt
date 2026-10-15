package com.zham.design.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class WeekDayColumn(
    val label: String,
    val dayNumber: String,
    val planned: List<TimelineBlock>,
    val actual: List<TimelineBlock>,
    val isToday: Boolean,
)

/**
 * The week in the same language as the day: plan on the left of each column, actual on the right.
 * The window is clipped to waking hours so the strip stays readable at this size.
 */
@Composable
fun WeekStrip(
    days: List<WeekDayColumn>,
    fromHour: Int,
    toHour: Int,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
) {
    val windowStart = fromHour * 60
    val windowEnd = toHour * 60
    val line = ZhamTheme.extra.line

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { index, day ->
            Column(
                Modifier.weight(1f).clickable { onDayClick(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(ZhamShapes.Block)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (day.isToday) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    ZhamShapes.Block,
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(Modifier.fillMaxSize()) {
                        MiniLane(day.planned, windowStart, windowEnd, true, Modifier.weight(1f))
                        Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                        MiniLane(day.actual, windowStart, windowEnd, false, Modifier.weight(1f))
                    }
                }
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        ZhamTheme.extra.slate
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    day.dayNumber,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MiniLane(
    blocks: List<TimelineBlock>,
    windowStart: Int,
    windowEnd: Int,
    planned: Boolean,
    modifier: Modifier = Modifier,
) {
    val span = (windowEnd - windowStart).coerceAtLeast(1)
    Layout(
        modifier = modifier.fillMaxHeight(),
        content = {
            blocks.forEach { block ->
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .clip(ZhamShapes.Pill)
                        .background(
                            if (planned) block.color.copy(alpha = 0.22f) else block.color
                        )
                )
            }
        },
    ) { measurables, constraints ->
        val fullHeight = constraints.maxHeight
        val placed = measurables.mapIndexed { index, measurable ->
            val block = blocks[index]
            val top = ((block.startMinutes - windowStart).coerceAtLeast(0) / span.toFloat() *
                fullHeight).toInt()
            val bottom = ((block.endMinutes - windowStart).coerceAtMost(span) / span.toFloat() *
                fullHeight).toInt()
            val blockHeight = (bottom - top).coerceAtLeast(2)
            measurable.measure(Constraints.fixed(constraints.maxWidth, blockHeight)) to top
        }
        layout(constraints.maxWidth, fullHeight) {
            placed.forEach { (placeable, top) -> placeable.place(0, top) }
        }
    }
}
