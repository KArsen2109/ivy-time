package com.zham.app.plan

import com.zham.app.ui.rememberDeleteConfirm
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.FieldButton
import com.zham.design.system.PrimaryButton
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.Spacing
import com.zham.design.system.TimeKeypadDialog
import com.zham.app.ui.CalendarSheet
import com.zham.app.ui.PickerRow
import java.time.DayOfWeek

/** Minutes before the start, as offered in the reminder row. */
private val REMINDER_CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

private fun reminderLabel(minutes: Int): String = when {
    minutes == 0 -> "At start"
    minutes == 1440 -> "1 day before"
    minutes >= 60 -> "${minutes / 60} h before"
    else -> "$minutes min before"
}

private val LENGTHS = listOf(15L, 30L, 45L, 60L, 90L, 120L, 180L, 240L)

@Composable
fun PlanEditorScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PlanEditorViewModel = hiltViewModel()
    val state = viewModel.uiState()

    LaunchedEffect(viewModel.finished) {
        if (viewModel.finished) onClose()
    }

    PlanEditorUi(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun PlanEditorUi(
    state: PlanEditorState,
    onEvent: (PlanEditorEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notificationsBlocked by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsBlocked = !granted }
    var datePicker by remember { mutableStateOf(false) }
    var startPicker by remember { mutableStateOf(false) }
    var endPicker by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (state.editing) "Edit block" else "Plan a block",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.editing) {
                val askDelete = rememberDeleteConfirm(
                    "Delete this block?", "Every day it repeats on goes away too.",
                ) { onEvent(PlanEditorEvent.Delete) }
                IconButton(onClick = askDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete block")
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldButton(
                label = "Starts",
                value = state.startLabel,
                onClick = { startPicker = true },
                modifier = Modifier.weight(1f),
            )
            FieldButton(
                label = if (state.crossesMidnight) "Ends next day" else "Ends",
                value = state.endLabel,
                onClick = { endPicker = true },
                modifier = Modifier.weight(1f),
            )
            FieldButton(
                label = "Length",
                value = state.durationLabel,
                onClick = { endPicker = true },
                modifier = Modifier.weight(1f),
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = Spacing.Gutter),
        ) {
            items(LENGTHS) { minutes ->
                ChoiceChip(
                    label = if (minutes >= 60) {
                        if (minutes % 60 == 0L) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
                    } else {
                        "${minutes}m"
                    },
                    selected = false,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onEvent(PlanEditorEvent.SetLength(minutes)) },
                )
            }
        }

        SectionLabel("Category")
        if (state.categories.isEmpty()) {
            EmptyNote("No categories yet. Add them in More.")
        } else {
            PickerRow(
                items = state.categories,
                selectedId = state.categoryId,
                onSelect = { onEvent(PlanEditorEvent.SelectCategory(it)) },
            )
        }

        if (state.projects.isNotEmpty()) {
            SectionLabel("Project")
            PickerRow(
                items = state.projects,
                selectedId = state.projectId,
                onSelect = { onEvent(PlanEditorEvent.SelectProject(it)) },
            )
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(PlanEditorEvent.SetNote(it)) },
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter, vertical = 16.dp),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = { onEvent(PlanEditorEvent.SetDescription(it)) },
            label = { Text("Description (optional)") },
            minLines = 2,
            maxLines = 5,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter),
        )

        SectionLabel("Repeats")
        Segmented(
            options = RepeatChoice.entries,
            selected = state.repeat,
            label = { it.name },
            onSelect = { onEvent(PlanEditorEvent.SetRepeat(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter),
        )

        if (state.repeat == RepeatChoice.Weekly) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    ChoiceChip(
                        label = day.name.take(1),
                        selected = day in state.weekdays,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onEvent(PlanEditorEvent.ToggleWeekday(day)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldButton(
            label = if (state.repeat == RepeatChoice.Once) "On" else "Starting",
            value = state.dateLabel,
            onClick = { datePicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter),
        )

        if (state.movedToNextDay) {
            Text(
                text = "That time has already passed today, so this block goes on the next day.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )
        }

        SectionLabel("Reminder")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = Spacing.Gutter),
        ) {
            item {
                ChoiceChip(
                    label = "Off",
                    selected = state.reminderMinutes == null,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onEvent(PlanEditorEvent.SetReminder(null)) },
                )
            }
            items(REMINDER_CHOICES) { minutes ->
                ChoiceChip(
                    label = reminderLabel(minutes),
                    selected = state.reminderMinutes == minutes,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onEvent(PlanEditorEvent.SetReminder(minutes))
                        if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
        if (state.reminderMinutes != null && notificationsBlocked) {
            Text(
                text = "Zham is not allowed to show notifications. Turn them on in the phone's settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Save block",
            onClick = { onEvent(PlanEditorEvent.Save) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter),
        )
    }

    if (datePicker) {
        CalendarSheet(
            title = if (state.repeat == RepeatChoice.Once) "Day of this block" else "First day of this block",
            selected = state.date,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                onEvent(PlanEditorEvent.SetDate(it))
                datePicker = false
            },
            onDismiss = { datePicker = false },
            enabled = { state.editing || !it.isBefore(state.today) },
        )
    }
    if (startPicker) {
        TimeKeypadDialog(
            title = "Block starts",
            initial = state.start,
            now = state.now,
            onDismiss = { startPicker = false },
            onSet = {
                onEvent(PlanEditorEvent.SetStart(it))
                startPicker = false
            },
        )
    }
    if (endPicker) {
        TimeKeypadDialog(
            title = "Block ends",
            initial = state.end,
            now = state.now,
            onDismiss = { endPicker = false },
            onSet = {
                onEvent(PlanEditorEvent.SetEnd(it))
                endPicker = false
            },
        )
    }
}
