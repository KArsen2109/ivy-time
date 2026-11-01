@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zham.app.day

import java.time.LocalTime
import com.zham.app.ui.rememberDeleteConfirm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ChoiceChip
import com.zham.design.system.ColorDot
import com.zham.design.system.DayTimeline
import com.zham.design.system.ZhamShapes
import com.zham.design.system.ZhamTheme
import com.zham.design.system.MeterBar
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.Spacing
import com.zham.design.system.StatPair
import com.zham.app.ui.CalendarSheet
import com.zham.design.system.ZhamIcons
import java.time.LocalDate

@Composable
fun DayScreen(
    onPlanAt: (LocalDate, Int) -> Unit,
    onEditPlan: (Long) -> Unit,
    onEditEntry: (Long) -> Unit,
    onLogTime: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DayViewModel = hiltViewModel()
    DayUi(
        state = viewModel.uiState(),
        onEvent = viewModel::onEvent,
        onPlanAt = onPlanAt,
        onEditPlan = onEditPlan,
        onEditEntry = onEditEntry,
        onLogTime = onLogTime,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayUi(
    state: DayState,
    onEvent: (DayEvent) -> Unit,
    onPlanAt: (LocalDate, Int) -> Unit,
    onEditPlan: (Long) -> Unit,
    onEditEntry: (Long) -> Unit,
    onLogTime: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    var calendarVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // Open on the first thing that happens, not on midnight.
    LaunchedEffect(state.date, state.firstContentMinutes) {
        val target = (state.nowMinutes ?: state.firstContentMinutes) - 60
        scroll.scrollTo(
            with(density) {
                (Spacing.HourHeight * (target.coerceAtLeast(0) / 60f)).roundToPx()
            }
        )
    }

    Column(modifier.fillMaxSize()) {
        DateBar(state, onEvent, onPickDate = { calendarVisible = true })
        Summary(state, onEvent, onPlanAt, onLogTime)
        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp),
            )
        }
        LaneHeader()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(bottom = 32.dp)
        ) {
            DayTimeline(
                planned = state.planned,
                actual = state.actual,
                nowMinutes = state.nowMinutes,
                onPlannedClick = { onEvent(DayEvent.OpenPlan(it)) },
                onActualClick = onEditEntry,
                onPlanAt = { minutes -> onPlanAt(state.date, minutes) },
                modifier = Modifier.padding(end = Spacing.Gutter, top = 12.dp),
            )
        }
    }

    if (calendarVisible) {
        CalendarSheet(
            title = "Show a day",
            selected = state.date,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                onEvent(DayEvent.SetDay(it))
                calendarVisible = false
            },
            onDismiss = { calendarVisible = false },
        )
    }

    var earlySheet by remember { mutableStateOf<PlanSheet?>(null) }
    earlySheet?.let { early ->
        EarlyDoneDialog(
            sheet = early,
            onDismiss = { earlySheet = null },
            onSave = { start, end ->
                onEvent(DayEvent.CompletePlanEarly(early.planId, early.date, start, end))
                earlySheet = null
            },
        )
    }

    val sheet = state.sheet
    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(DayEvent.CloseSheet) },
            sheetState = rememberModalBottomSheetState(),
            shape = ZhamShapes.Sheet,
        ) {
            val askDelete = rememberDeleteConfirm(
                "Delete this block?", "Every day it repeats on goes away too.",
            ) { onEvent(DayEvent.DeletePlan(sheet.planId)) }
            PlanSheetContent(
                sheet = sheet,
                onStart = { onEvent(DayEvent.StartFromPlan(sheet.planId)) },
                onDone = {
                    if (!sheet.done && sheet.startsInFuture && !sheet.point) {
                        earlySheet = sheet
                    } else {
                        onEvent(DayEvent.TogglePlanDone(sheet.planId, sheet.date, sheet.done))
                    }
                },
                onEdit = {
                    onEvent(DayEvent.CloseSheet)
                    onEditPlan(sheet.planId)
                },
                onDelete = askDelete,
            )
        }
    }
}

@Composable
private fun DateBar(state: DayState, onEvent: (DayEvent) -> Unit, onPickDate: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.Gutter, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.dateLabel, style = MaterialTheme.typography.headlineMedium)
            Text(
                state.weekdayLabel,
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
            )
        }
        if (!state.isToday) {
            QuietButton(
                text = "Today",
                onClick = { onEvent(DayEvent.GoToToday) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = onPickDate) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick a date")
        }
        IconButton(onClick = { onEvent(DayEvent.ShiftDay(-1)) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
        }
        IconButton(onClick = { onEvent(DayEvent.ShiftDay(1)) }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun Summary(
    state: DayState,
    onEvent: (DayEvent) -> Unit,
    onPlanAt: (LocalDate, Int) -> Unit,
    onLogTime: (LocalDate) -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.Gutter, vertical = 12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPair("planned", state.plannedLabel, MaterialTheme.colorScheme.onSurface)
            StatPair("tracked", state.trackedLabel, MaterialTheme.colorScheme.onSurface)
            StatPair(
                label = if (state.keptPercent > 0) "of plan kept" else "unplanned",
                value = if (state.keptPercent > 0) "${state.keptPercent}%" else state.unplannedLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Like a balance: what is left of the day, and what of it is not spoken for.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPair(if (state.isToday) "left today" else "in the day", state.leftLabel, MaterialTheme.colorScheme.onSurface)
            StatPair("free after plans", state.freeLabel, MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        MeterBar(
            fraction = state.adherence,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))

        val running = state.running
        if (running != null) {
            RunningBar(running, onStop = { onEvent(DayEvent.StopTimer) })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton(
                    text = "Plan a block",
                    onClick = { onPlanAt(state.date, 9 * 60) },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Log time",
                    onClick = { onLogTime(state.date) },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.quickCategories.isNotEmpty() && state.isToday) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.quickCategories, key = { it.id }) { category ->
                        ChoiceChip(
                            label = category.name,
                            selected = false,
                            color = category.color,
                            glyph = category.icon,
                            onClick = { onEvent(DayEvent.QuickStart(category.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningBar(running: RunningUi, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(ZhamTheme.extra.signal, size = 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                running.elapsed,
                style = MaterialTheme.typography.displaySmall,
                color = ZhamTheme.extra.signal,
                fontWeight = FontWeight.Bold,
            )
            Text(
                running.title + " · " + running.since,
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
            )
        }
        QuietButton(text = "Stop", onClick = onStop, icon = Icons.Rounded.Stop)
    }
}

@Composable
private fun LaneHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.HourGutter, end = Spacing.Gutter, bottom = 6.dp)
    ) {
        Text(
            "plan",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = ZhamTheme.extra.slate,
        )
        Text(
            "actual",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = ZhamTheme.extra.slate,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PlanSheetContent(
    sheet: PlanSheet,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter)
            .padding(bottom = 32.dp)
    ) {
        Text(sheet.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            sheet.timeRange + " · " + sheet.repeatLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = ZhamTheme.extra.slate,
        )
        if (sheet.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(sheet.description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!sheet.point) {
                PrimaryButton(
                    text = "Start now",
                    onClick = onStart,
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                )
            }
            QuietButton(
                text = if (sheet.done) "Done · undo" else "Mark done",
                onClick = onDone,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(text = "Edit", onClick = onEdit, modifier = Modifier.weight(1f))
            QuietButton(
                text = "Delete",
                onClick = onDelete,
                icon = Icons.Rounded.Delete,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Asks when a block planned for later was really done, so the record carries the real times. */
@Composable
private fun EarlyDoneDialog(sheet: PlanSheet, onDismiss: () -> Unit, onSave: (LocalTime, LocalTime) -> Unit) {
    val defaultStart = remember(sheet) {
        // Assume it was done just now and took as long as planned, but never before midnight.
        sheet.now.minusMinutes(sheet.plannedMinutes).let { if (it.isAfter(sheet.now)) LocalTime.MIDNIGHT else it }
    }
    var start by remember(sheet) { mutableStateOf(defaultStart) }
    var end by remember(sheet) { mutableStateOf(sheet.now) }
    var picking by remember { mutableStateOf<String?>(null) }
    val formatter = remember(sheet.is24Hour) {
        java.time.format.DateTimeFormatter.ofPattern(if (sheet.is24Hour) "HH:mm" else "h:mm a", java.util.Locale.ENGLISH)
    }
    val valid = start.isBefore(end)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Done early") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This was planned for ${sheet.timeRange}. Enter when you really did it; " +
                        "those times are what get recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.zham.design.system.FieldButton(
                        label = "Started",
                        value = formatter.format(start),
                        onClick = { picking = "start" },
                        modifier = Modifier.weight(1f),
                    )
                    com.zham.design.system.FieldButton(
                        label = "Finished",
                        value = formatter.format(end),
                        onClick = { picking = "end" },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Text(
                        "It has to finish after it started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = valid, onClick = { onSave(start, end) }) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picking) {
        "start" -> com.zham.design.system.TimeDialog(
            title = "Started",
            initial = start,
            now = sheet.now,
            is24Hour = sheet.is24Hour,
            onDismiss = { picking = null },
            onSet = {
                start = it
                picking = null
            },
            isAllowed = { !it.isAfter(sheet.now) },
            blockedHint = "That time has not come yet today",
        )
        "end" -> com.zham.design.system.TimeDialog(
            title = "Finished",
            initial = end,
            now = sheet.now,
            is24Hour = sheet.is24Hour,
            onDismiss = { picking = null },
            onSet = {
                end = it
                picking = null
            },
            isAllowed = { !it.isAfter(sheet.now) },
            blockedHint = "That time has not come yet today",
        )
    }
}
