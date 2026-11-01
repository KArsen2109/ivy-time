package com.zham.app.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.PickerRow
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.ScreenHeading
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme
import com.zham.domain.model.PresetKind
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PresetsScreen(modifier: Modifier = Modifier) {
    val viewModel: PresetsViewModel = hiltViewModel()
    val state = viewModel.uiState()
    var edited by remember { mutableStateOf<PresetItem?>(null) }
    var dialog by remember { mutableStateOf(false) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Standard entries") {
                IconButton(onClick = { edited = null; dialog = true }) { Icon(Icons.Rounded.Add, contentDescription = "New standard entry") }
            }
            EmptyNote(
                "A standard entry holds everything already filled in: a bus fare of 300 from cash, or " +
                    "9 hours of Work. Tap it once and the record is made."
            )
        }
        items(state.presets, key = { it.id }) { preset ->
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp), onClick = { edited = preset; dialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (preset.kind.name + " · " + preset.summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    val askDelete = rememberDeleteConfirm("Delete this standard entry?", "Records already made stay.") {
                        viewModel.onEvent(PresetsEvent.Delete(preset.id))
                    }
                    IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.Gutter)) } }
    }

    if (dialog) {
        val initial = edited
        var kind by remember { mutableStateOf(initial?.kind ?: PresetKind.Expense) }
        var name by remember { mutableStateOf(initial?.name.orEmpty()) }
        var title by remember { mutableStateOf(initial?.title.orEmpty()) }
        var categoryId by remember { mutableStateOf(initial?.categoryId) }
        var projectId by remember { mutableStateOf(initial?.projectId) }
        var accountId by remember { mutableStateOf(initial?.accountId) }
        var amount by remember { mutableStateOf(initial?.amount?.takeIf { it != "0" }.orEmpty()) }
        var hours by remember { mutableStateOf(initial?.hours?.takeIf { it != "0" }.orEmpty()) }
        var minutes by remember { mutableStateOf(initial?.minutes?.takeIf { it != "0" }.orEmpty()) }
        val money = kind != PresetKind.Activity

        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(if (initial == null) "New standard entry" else "Edit standard entry") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Segmented(
                        options = PresetKind.entries, selected = kind,
                        label = { if (it == PresetKind.Activity) "Activity" else it.name },
                        onSelect = { kind = it; categoryId = null; projectId = null },
                        modifier = Modifier.fillMaxWidthCompat(),
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Name on the button") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(title, { title = it }, label = { Text("Title of the record") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                    if (money) {
                        OutlinedTextField(
                            amount, { amount = it }, label = { Text("Amount") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium,
                        )
                        Text("Account", style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                        PickerRow(state.accounts, accountId, onSelect = { accountId = it })
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                        PickerRow(state.moneyCategories, categoryId, onSelect = { categoryId = it })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                hours, { hours = it.filter(Char::isDigit).take(2) }, label = { Text("Hours") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
                            )
                        }
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                        PickerRow(state.timeCategories, categoryId, onSelect = { categoryId = it; projectId = null })
                        val inCategory = state.projects.filter { it.first == categoryId }.map { it.second }
                        if (categoryId != null && inCategory.isNotEmpty()) {
                            Text("Project", style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                            PickerRow(inCategory.toImmutableList(), projectId, onSelect = { projectId = it })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.onEvent(PresetsEvent.Save(initial?.id ?: 0L, kind, name, title, categoryId, projectId, accountId, amount, hours, minutes))
                    dialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
        )
    }
}

private fun Modifier.fillMaxWidthCompat(): Modifier = this.then(Modifier.fillMaxWidth())

/** A row of standard activities; one tap logs the record. */
@Composable
fun ActivityPresetRow(modifier: Modifier = Modifier) {
    val viewModel: ActivityPresetsViewModel = hiltViewModel()
    val state = viewModel.uiState()
    if (state.chips.isEmpty() && state.message == null) return
    Column(modifier) {
        if (state.chips.isNotEmpty()) {
            SectionLabel("Standard")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = Spacing.Gutter),
            ) {
                items(state.chips, key = { it.id }) { chip ->
                    ChoiceChip(
                        label = chip.name + " · " + chip.amount,
                        selected = false,
                        color = chip.color,
                        onClick = { viewModel.onEvent(ActivityPresetsEvent.Apply(chip.id)) },
                    )
                }
            }
        }
        state.message?.let {
            Row(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { viewModel.onEvent(ActivityPresetsEvent.Dismiss) }) { Text("OK") }
            }
        }
    }
}
