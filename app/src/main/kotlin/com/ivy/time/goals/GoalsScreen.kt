package com.ivy.time.goals

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients
import com.ivy.design.system.IvyProgressBar
import com.ivy.design.system.colors.IvyColors
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalType
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.PickerRow
import com.ivy.time.ui.ScreenTitle

@Composable
fun GoalsScreen(modifier: Modifier = Modifier) {
    val viewModel: GoalsViewModel = hiltViewModel()
    GoalsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun GoalsUi(state: GoalsState, onEvent: (GoalsEvent) -> Unit, modifier: Modifier = Modifier) {
    var edited by remember { mutableStateOf<GoalItem?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        ) {
            item { ScreenTitle("Goals") }
            if (state.goals.isEmpty()) {
                item {
                    EmptyHint(
                        "Set how much time a category deserves: at least 5h of Sport per week, " +
                            "at most 3h of Social. Ivy Wallet calls these budgets."
                    )
                }
            }
            items(state.goals, key = { it.id }) { goal ->
                IvyCard(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        edited = goal
                        dialogVisible = true
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                goal.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                goal.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { onEvent(GoalsEvent.Delete(goal.id)) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete goal")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    IvyProgressBar(
                        fraction = goal.fraction,
                        color = if (goal.met) IvyColors.Green.primary else goal.color,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            goal.progressLabel,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (goal.met) "on track" else "not yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (goal.met) IvyColors.Green.primary else MaterialTheme.colorScheme.outline,
                        )
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
            Icon(Icons.Rounded.Add, contentDescription = "New goal", tint = Color.White)
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GoalType.entries.toList()) { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = {
                                Text(if (option == GoalType.AtLeast) "At least" else "At most")
                            },
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GoalPeriod.entries.toList()) { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text("Per " + option.name.lowercase()) },
                        )
                    }
                }
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
