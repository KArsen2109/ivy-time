package com.zham.app.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.FieldButton
import com.zham.design.system.PrimaryButton
import com.zham.design.system.SectionLabel
import com.zham.design.system.Spacing
import com.zham.design.system.TimeKeypadDialog
import com.zham.app.ui.CalendarSheet
import com.zham.app.ui.PickerRow

private val QUICK_MINUTES = listOf(15L, 30L, 60L, 120L, 240L, 480L)

@Composable
fun EntryScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: EntryViewModel = hiltViewModel()
    val state = viewModel.uiState()

    LaunchedEffect(viewModel.finished) {
        if (viewModel.finished) onClose()
    }

    EntryUi(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun EntryUi(
    state: EntryState,
    onEvent: (EntryEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var datePickerVisible by remember { mutableStateOf(false) }
    var startPickerVisible by remember { mutableStateOf(false) }
    var endPickerVisible by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (state.editing) "Edit record" else "Log time",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.editing) {
                IconButton(onClick = { onEvent(EntryEvent.Delete) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                }
            }
        }

        SectionLabel("Category")
        if (state.categories.isEmpty()) {
            EmptyNote("No categories yet. Add them in More.")
        } else {
            PickerRow(
                items = state.categories,
                selectedId = state.categoryId,
                onSelect = { onEvent(EntryEvent.SelectCategory(it)) },
            )
        }

        if (state.projects.isNotEmpty()) {
            SectionLabel("Project")
            PickerRow(
                items = state.projects,
                selectedId = state.projectId,
                onSelect = { onEvent(EntryEvent.SelectProject(it)) },
            )
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(EntryEvent.SetNote(it)) },
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = { onEvent(EntryEvent.SetDescription(it)) },
            label = { Text("Description (optional)") },
            minLines = 2,
            maxLines = 5,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldButton(
                label = "Date",
                value = state.dateLabel,
                onClick = { datePickerVisible = true },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldButton(
                label = "Start",
                value = state.startLabel,
                onClick = { startPickerVisible = true },
                modifier = Modifier.weight(1f),
            )
            FieldButton(
                label = if (state.crossesMidnight) "End (next day)" else "End",
                value = state.endLabel,
                onClick = { endPickerVisible = true },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = state.durationLabel,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 16.dp),
        )

        SectionLabel("Quick length")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(QUICK_MINUTES) { minutes ->
                ChoiceChip(
                    label = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                    selected = false,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onEvent(EntryEvent.SetDuration(minutes)) },
                )
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )
        }

        PrimaryButton(
            text = "Save record",
            onClick = { onEvent(EntryEvent.Save) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 16.dp),
        )
    }

    if (datePickerVisible) {
        CalendarSheet(
            title = "Date of this record",
            selected = state.date,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                onEvent(EntryEvent.SetDate(it))
                datePickerVisible = false
            },
            onDismiss = { datePickerVisible = false },
        )
    }
    if (startPickerVisible) {
        TimeKeypadDialog(
            title = "Start time",
            initial = state.start,
            now = state.now,
            onDismiss = { startPickerVisible = false },
            onSet = {
                onEvent(EntryEvent.SetStart(it))
                startPickerVisible = false
            },
        )
    }
    if (endPickerVisible) {
        TimeKeypadDialog(
            title = "End time",
            initial = state.end,
            now = state.now,
            onDismiss = { endPickerVisible = false },
            onSet = {
                onEvent(EntryEvent.SetEnd(it))
                endPickerVisible = false
            },
        )
    }
}
