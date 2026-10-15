@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zham.app.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ColorDot
import com.zham.design.system.EmptyNote
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamShapes
import com.zham.design.system.ZhamTheme
import com.zham.design.system.MeterBar
import com.zham.design.system.SectionLabel
import com.zham.design.system.Spacing
import com.zham.design.system.StatPair
import com.zham.design.system.WeekStrip
import com.zham.app.ui.CalendarSheet
import java.time.LocalDate

@Composable
fun WeekScreen(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WeekViewModel = hiltViewModel()
    WeekUi(viewModel.uiState(), viewModel::onEvent, onOpenDay, modifier)
}

@Composable
fun WeekUi(
    state: WeekState,
    onEvent: (WeekEvent) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var calendarVisible by remember { mutableStateOf(false) }
    var openGoalId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.Gutter, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhamTheme.extra.slate,
                    )
                }
                IconButton(onClick = { calendarVisible = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick a date")
                }
                IconButton(onClick = { onEvent(WeekEvent.ShiftWeek(-1)) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous week")
                }
                IconButton(onClick = { onEvent(WeekEvent.ShiftWeek(1)) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next week")
                }
            }

            FlowRow(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPair("planned", state.plannedLabel, MaterialTheme.colorScheme.onSurface)
                StatPair("tracked", state.trackedLabel, MaterialTheme.colorScheme.onSurface)
                StatPair(
                    "of plan kept",
                    "${state.keptPercent}%",
                    MaterialTheme.colorScheme.primary,
                )
            }

            Column(Modifier.padding(horizontal = Spacing.Gutter)) {
                WeekStrip(
                    days = state.days,
                    fromHour = 6,
                    toHour = 24,
                    onDayClick = { index -> state.dates.getOrNull(index)?.let(onOpenDay) },
                )
            }

            Text(
                "Busiest day: " + state.busiestLabel,
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 12.dp),
            )

            SectionLabel("Goals")
            if (state.goals.isEmpty()) {
                EmptyNote("No goals yet. Set them in More to hold a category to a weekly target.")
            }
        }
        items(state.goals, key = { it.id }) { goal ->
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = { openGoalId = goal.id },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goal.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (goal.met) "on track" else "behind",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.met) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            ZhamTheme.extra.slate
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                MeterBar(
                    fraction = goal.fraction,
                    color = if (goal.met) MaterialTheme.colorScheme.primary else goal.color,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    goal.detail + " · " + goal.recordCount + if (goal.recordCount == 1) " record" else " records",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhamTheme.extra.slate,
                )
            }
        }
    }

    if (calendarVisible) {
        CalendarSheet(
            title = "Show a day",
            selected = state.today,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                calendarVisible = false
                onOpenDay(it)
            },
            onDismiss = { calendarVisible = false },
        )
    }

    state.goals.firstOrNull { it.id == openGoalId }?.let { goal ->
        GoalSheet(goal = goal, onDismiss = { openGoalId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(goal: GoalRow, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZhamShapes.Sheet,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Spacing.Gutter, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Text(goal.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    goal.detail + " · " + goal.periodLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZhamTheme.extra.slate,
                )
                Spacer(Modifier.height(10.dp))
                MeterBar(
                    fraction = goal.fraction,
                    color = if (goal.met) MaterialTheme.colorScheme.primary else goal.color,
                )
                if (goal.days.isEmpty()) {
                    EmptyNote("No records counted toward this goal yet.")
                }
            }
            goal.days.forEach { day ->
                item(key = "day-" + day.title) {
                    Row(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                        Text(
                            day.title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhamTheme.extra.slate,
                        )
                        Text(
                            day.total,
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                }
                items(day.records) { record ->
                    Row(
                        Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(record.color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(record.path, record.range).filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhamTheme.extra.slate,
                            )
                            if (record.description.isNotBlank()) {
                                Text(
                                    record.description,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text(record.duration, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}
