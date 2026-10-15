package com.zham.app.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
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
import com.zham.design.system.EmptyNote
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme
import com.zham.design.system.MeterBar
import com.zham.design.system.ScreenHeading
import com.zham.design.system.Segmented
import com.zham.design.system.Spacing
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalType
import com.zham.app.ui.PickerItem
import com.zham.app.ui.PickerRow

@Composable
fun GoalsScreen(modifier: Modifier = Modifier) {
    val viewModel: GoalsViewModel = hiltViewModel()
    GoalsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun GoalsUi(state: GoalsState, onEvent: (GoalsEvent) -> Unit, modifier: Modifier = Modifier) {
    var edited by remember { mutableStateOf<GoalItem?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("Goals") {
                IconButton(
                    onClick = {
                        edited = null
                        dialogVisible = true
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New goal")
                }
            }
            if (state.goals.isEmpty()) {
                EmptyNote(
                    "Hold a category to a target: at least 5h of Sport a week, " +
                        "at most 3h of Social."
                )
            }
        }
        items(state.goals, key = { it.id }) { goal ->
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = {
                    edited = goal
                    dialogVisible = true
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            goal.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    IconButton(onClick = { onEvent(GoalsEvent.Delete(goal.id)) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete goal")
                    }
                }
                Spacer(Modifier.height(12.dp))
                MeterBar(
                    fraction = goal.fraction,
                    color = if (goal.met) MaterialTheme.colorScheme.primary else goal.color,
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goal.progressLabel,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
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
            }
        }
        state.error?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
                )
            }
        }
    }

    if (dialogVisible) {
        GoalDialog(
            initial = edited,
            categories = state.categories,
            onDismiss = { dialogVisible = false },
            onSave = { id, name, categoryId, minutes, period, type ->
                onEvent(GoalsEvent.Save(id, name, categoryId, minutes, period, type))
                dialogVisible = false
            },
        )
    }
}

@Composable
private fun GoalDialog(
    initial: GoalItem?,
    categories: List<PickerItem>,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long?, Long, GoalPeriod, GoalType) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var hoursText by remember {
        mutableStateOf(((initial?.minutes ?: 300L) / 60).toString())
    }
    var period by remember { mutableStateOf(initial?.period ?: GoalPeriod.Week) }
    var type by remember { mutableStateOf(initial?.type ?: GoalType.AtLeast) }

    val hours = hoursText.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                if (categories.isNotEmpty()) {
                    PickerRow(
                        items = categories,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )
                }
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Hours") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Segmented(
                    options = GoalType.entries,
                    selected = type,
                    label = { if (it == GoalType.AtLeast) "At least" else "At most" },
                    onSelect = { type = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Segmented(
                    options = GoalPeriod.entries,
                    selected = period,
                    label = { "Per " + it.name.lowercase() },
                    onSelect = { period = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && hours != null,
                onClick = {
                    onSave(initial?.id ?: 0L, name, categoryId, (hours ?: 0L) * 60, period, type)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
