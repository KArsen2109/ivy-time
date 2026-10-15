package com.ivy.design.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TimelineBlock(
    val id: Long,
    val startMinutes: Int,
    val endMinutes: Int,
    val label: String,
    val detail: String,
    val color: Color,
    val done: Boolean = false,
    val running: Boolean = false,
)

const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * The day as one hour ruler with two lanes beside it: what was planned, and what happened.
 * The gap between the lanes is the point of the screen, so both are drawn to the same scale.
 */
@Composable
fun DayTimeline(
    planned: List<TimelineBlock>,
    actual: List<TimelineBlock>,
    nowMinutes: Int?,
    onPlannedClick: (Long) -> Unit,
    onActualClick: (Long) -> Unit,
    onPlanAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hourHeight: Dp = Spacing.HourHeight,
) {
    val line = IvyTheme.extra.line
    val slate = IvyTheme.extra.slate

    Row(modifier.height(hourHeight * 24)) {
        Column(Modifier.width(Spacing.HourGutter).fillMaxHeight()) {
            repeat(24) { hour ->
                Box(Modifier.height(hourHeight).fillMaxWidth()) {
                    Text(
                        text = "%02d".format(hour),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp)
                            .offset(y = (-7).dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = slate,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            // The hour rules are the only structural lines in the app.
            Column(Modifier.fillMaxSize()) {
                repeat(24) {
                    Box(Modifier.height(hourHeight).fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                    }
                }
            }

            Row(Modifier.fillMaxSize()) {
                Lane(
                    blocks = planned,
                    hourHeight = hourHeight,
                    planned = true,
                    onClick = onPlannedClick,
                    onEmptyTap = onPlanAt,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(6.dp))
                Lane(
                    blocks = actual,
                    hourHeight = hourHeight,
                    planned = false,
                    onClick = onActualClick,
                    onEmptyTap = null,
                    modifier = Modifier.weight(1f),
                )
            }

            if (nowMinutes != null) {
                NowLine(nowMinutes, hourHeight)
            }
        }
    }
}

@Composable
private fun Lane(
    blocks: List<TimelineBlock>,
    hourHeight: Dp,
    planned: Boolean,
    onClick: (Long) -> Unit,
    onEmptyTap: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val columns = assignColumns(blocks)
    val lanes = (columns.values.maxOrNull() ?: 0) + 1

    val tappable = if (onEmptyTap != null) {
        Modifier.pointerInput(hourHeight) {
            val hourPx = hourHeight.toPx()
            detectTapGestures { offset ->
                val minutes = (offset.y / hourPx * 60f).toInt()
                // Snapping to the quarter hour keeps blocks tidy without asking for a picker.
                onEmptyTap((minutes / 15 * 15).coerceIn(0, MINUTES_PER_DAY - 15))
            }
        }
    } else {
        Modifier
    }

    Layout(
        modifier = modifier.fillMaxHeight().then(tappable),
        content = {
            blocks.forEach { block ->
                BlockView(block = block, planned = planned, onClick = { onClick(block.id) })
            }
        },
    ) { measurables, constraints ->
        val laneWidth = constraints.maxWidth
        val columnWidth = (laneWidth / lanes).coerceAtLeast(1)
        val hourPx = hourHeight.toPx()
        val placed = measurables.mapIndexed { index, measurable ->
            val block = blocks[index]
            val minutes = (block.endMinutes - block.startMinutes).coerceAtLeast(12)
            val height = (minutes / 60f * hourPx).toInt().coerceAtLeast(1)
            measurable.measure(Constraints.fixed(columnWidth, height)) to block
        }
        layout(laneWidth, constraints.maxHeight) {
            placed.forEach { (placeable, block) ->
                val column = columns[block.id] ?: 0
                placeable.place(
                    x = column * columnWidth,
                    y = (block.startMinutes / 60f * hourPx).toInt(),
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: TimelineBlock, planned: Boolean, onClick: () -> Unit) {
    val signal = IvyTheme.extra.signal
    Box(
        Modifier
            .fillMaxSize()
            .padding(end = 2.dp, top = 1.dp, bottom = 1.dp)
            .clip(IvyShapes.Block)
            .background(
                when {
                    planned && block.done -> block.color.copy(alpha = 0.08f)
                    planned -> block.color.copy(alpha = 0.14f)
                    else -> block.color
                }
            )
            .then(
                when {
                    planned -> Modifier.border(
                        width = 1.dp,
                        color = block.color.copy(alpha = if (block.done) 0.3f else 0.75f),
                        shape = IvyShapes.Block,
                    )
                    block.running -> Modifier.border(2.dp, signal, IvyShapes.Block)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Column {
            Text(
                text = block.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (planned) block.color else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (block.detail.isNotBlank()) {
                Text(
                    text = block.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (planned) {
                        block.color.copy(alpha = 0.8f)
                    } else {
                        Color.White.copy(alpha = 0.85f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowLine(nowMinutes: Int, hourHeight: Dp) {
    val signal = IvyTheme.extra.signal
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = hourHeight * (nowMinutes / 60f))
    ) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(signal))
        Box(
            Modifier
                .offset(y = (-3).dp)
                .height(8.dp)
                .width(8.dp)
                .clip(IvyShapes.Pill)
                .background(signal)
        )
    }
}

/** Side-by-side columns for blocks that overlap, so none is hidden behind another. */
private fun assignColumns(blocks: List<TimelineBlock>): Map<Long, Int> {
    val endByColumn = mutableListOf<Int>()
    val result = mutableMapOf<Long, Int>()
    blocks.sortedBy { it.startMinutes }.forEach { block ->
        val free = endByColumn.indexOfFirst { it <= block.startMinutes }
        if (free >= 0) {
            endByColumn[free] = block.endMinutes
            result[block.id] = free
        } else {
            endByColumn += block.endMinutes
            result[block.id] = endByColumn.lastIndex
        }
    }
    return result
}
