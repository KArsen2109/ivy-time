package com.ivy.time.planned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.ColorDot
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients
import com.ivy.design.system.IvyIcons
import com.ivy.design.system.SectionTitle
import com.ivy.time.domain.model.Recurrence
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.FieldButton
import com.ivy.time.ui.IvyDatePickerDialog
import com.ivy.time.ui.IvyTimePickerDialog
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.PickerRow
import com.ivy.time.ui.ScreenTitle
import com.ivy.time.ui.TimeFormatters
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PlannedScreen(modifier: Modifier = Modifier) {
    val viewModel: PlannedViewModel = hiltViewModel()
    PlannedUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun PlannedUi(
    state: PlannedState,
    onEvent: (PlannedEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var edited by remember { mutableStateOf<PlannedItem?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        ) {
            item { ScreenTitle("Planned") }
            if (state.overdue.isEmpty() && state.upcoming.isEmpty()) {
                item {
                    EmptyHint(
                        "Plan the time you intend to spend — a daily workout, a weekly review. " +
                            "When it happens, tap ✓ and it becomes a record."
                    )
                }
            }
            if (state.overdue.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Due", Modifier.weight(1f))
                        SectionTitle(state.overdueTotal)
                    }
                }
                items(state.overdue, key = { "due-${it.id}" }) { item ->
                    PlannedRow(item, onEvent) {
                        edited = item
                        dialogVisible = true
                    }
                }
            }
            if (state.upcoming.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Upcoming", Modifier.weight(1f))
                        SectionTitle(state.upcomingTotal)
                    }
                }
                items(state.upcoming, key = { "next-${it.id}" }) { item ->
                    PlannedRow(item, onEvent) {
                        edited = item
                        dialogVisible = true
                    }
                }
            }
            state.error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }

        GradientCircleButton(
            colors = IvyGradients.Ivy,
            onClick = {
                edited = null
                dialogVisible = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New plan", tint = Color.White)
        }
    }

    if (dialogVisible) {
        PlannedDialog(
            initial = edited,
            categories = state.categories,
            use24Hours = state.use24Hours,
            onDismiss = { dialogVisible = false },
            onSave = { id, categoryId, note, minutes, date, time, recurrence ->
                onEvent(PlannedEvent.Save(id, categoryId, note, minutes, date, time, recurrence))
                dialogVisible = false
            },
        )
    }
}

@Composable
private fun PlannedRow(
    item: PlannedItem,
    onEvent: (PlannedEvent) -> Unit,
    onClick: () -> Unit,
) {
    IvyCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.icon != null) {
                Icon(IvyIcons.of(item.icon), contentDescription = null, tint = item.color)
            } else {
                ColorDot(item.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                item.duration,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onEvent(PlannedEvent.Complete(item.id)) }) {
                Icon(Icons.Rounded.Check, contentDescription = "Log this plan", tint = item.color)
            }
            IconButton(onClick = { onEvent(PlannedEvent.Delete(item.id)) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete plan")
            }
        }
    }
}

@Composable
private fun PlannedDialog(
    initial: PlannedItem?,
    categories: List<PickerItem>,
    use24Hours: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String, Long, LocalDate, LocalTime, Recurrence) -> Unit,
) {
    val clock = remember { Clock.systemDefaultZone() }
    val formatters = remember(use24Hours) { TimeFormatters(clock.zone, use24Hours) }

    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var minutesText by remember { mutableStateOf((initial?.minutes ?: 60L).toString()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now(clock)) }
    var time by remember { mutableStateOf(initial?.time ?: LocalTime.of(9, 0)) }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: Recurrence.OneTime) }
    var datePickerVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }

    val minutes = minutesText.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New plan" else "Edit plan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (categories.isNotEmpty()) {
                    PickerRow(
                        items = categories,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What is planned?") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldButton(
                        label = "Date",
                        value = formatters.shortDate(date),
                        onClick = { datePickerVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                    FieldButton(
                        label = "Time",
                        value = formatters.time(time),
                        onClick = { timePickerVisible = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Recurrence.entries.toList()) { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null,
                onClick = {
                    onSave(initial?.id ?: 0L, categoryId, note, minutes ?: 0L, date, time, recurrence)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (datePickerVisible) {
        IvyDatePickerDialog(
            initial = date,
            onDismiss = { datePickerVisible = false },
            onPick = { date = it },
        )
    }
    if (timePickerVisible) {
        IvyTimePickerDialog(
            initial = time,
            use24Hours = use24Hours,
            onDismiss = { timePickerVisible = false },
            onPick = { time = it },
        )
    }
}
