package com.ivy.time.entry

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import com.ivy.design.system.SectionTitle
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.FieldButton
import com.ivy.time.ui.IvyDatePickerDialog
import com.ivy.time.ui.IvyTimePickerDialog
import com.ivy.time.ui.PickerRow

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
                text = if (state.editing) "Edit record" else "New record",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (state.editing) {
                IconButton(onClick = { onEvent(EntryEvent.Delete) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                }
            }
        }

        SectionTitle("Category")
        if (state.categories.isEmpty()) {
            EmptyHint("No categories yet. Create them in More → Categories.")
        } else {
            PickerRow(
                items = state.categories,
                selectedId = state.categoryId,
                onSelect = { onEvent(EntryEvent.SelectCategory(it)) },
            )
        }

        if (state.projects.isNotEmpty()) {
            SectionTitle("Project")
            PickerRow(
                items = state.projects,
                selectedId = state.projectId,
                onSelect = { onEvent(EntryEvent.SelectProject(it)) },
            )
        }

        OutlinedTextField(
            value = state.note,
            onValueChange = { onEvent(EntryEvent.SetNote(it)) },
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        SectionTitle("Quick duration")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(QUICK_MINUTES) { minutes ->
                AssistChip(
                    onClick = { onEvent(EntryEvent.SetDuration(minutes)) },
                    label = {
                        Text(if (minutes >= 60) "${minutes / 60}h" else "${minutes}m")
                    },
                )
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        Button(
            onClick = { onEvent(EntryEvent.Save) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Save")
        }
    }

    if (datePickerVisible) {
        IvyDatePickerDialog(
            initial = state.date,
            onDismiss = { datePickerVisible = false },
            onPick = { onEvent(EntryEvent.SetDate(it)) },
        )
    }
    if (startPickerVisible) {
        IvyTimePickerDialog(
            initial = state.start,
            use24Hours = state.use24Hours,
            onDismiss = { startPickerVisible = false },
            onPick = { onEvent(EntryEvent.SetStart(it)) },
        )
    }
    if (endPickerVisible) {
        IvyTimePickerDialog(
            initial = state.end,
            use24Hours = state.use24Hours,
            onDismiss = { endPickerVisible = false },
            onPick = { onEvent(EntryEvent.SetEnd(it)) },
        )
    }
}
