package com.zham.app.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.CalendarSheet
import com.zham.app.ui.PickerRow
import com.zham.app.ui.TitleSuggestions
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.EmptyNote
import com.zham.design.system.FieldButton
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.Spacing
import com.zham.design.system.TimeDialog
import com.zham.design.system.ZhamTheme
import com.zham.domain.model.TxType

@Composable
fun TxScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TxViewModel = hiltViewModel()
    val state = viewModel.uiState()
    LaunchedEffect(viewModel.finished) { if (viewModel.finished) onClose() }
    TxUi(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun TxUi(state: TxState, onEvent: (TxEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var standardDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                (if (state.editing) "Edit " else "New ") + state.type.name.lowercase(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (state.editing) {
                val askDelete = rememberDeleteConfirm("Delete this entry?", "The balance of the account changes back.") {
                    onEvent(TxEvent.Delete)
                }
                IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
            }
        }

        Segmented(
            options = TxType.entries,
            selected = state.type,
            label = { it.name },
            onSelect = { onEvent(TxEvent.SetType(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
        )

        OutlinedTextField(
            value = state.amount,
            onValueChange = { onEvent(TxEvent.SetAmount(it)) },
            label = { Text("Amount" + if (state.currency.isNotEmpty()) " (${state.currency})" else "") },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
        )

        SectionLabel(if (state.type == TxType.Transfer) "From account" else "Account")
        if (state.accounts.isEmpty()) {
            EmptyNote("No accounts yet. Add one in More, under Money.")
        } else {
            PickerRow(state.accounts, state.accountId, onSelect = { onEvent(TxEvent.SelectAccount(it)) })
        }

        if (state.type == TxType.Transfer) {
            SectionLabel("To account")
            PickerRow(
                state.accounts.filter { it.id != state.accountId },
                state.toAccountId,
                onSelect = { onEvent(TxEvent.SelectToAccount(it)) },
            )
            if (state.needsToAmount) {
                OutlinedTextField(
                    value = state.toAmount,
                    onValueChange = { onEvent(TxEvent.SetToAmount(it)) },
                    label = { Text("Arrives (${state.toCurrency})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
                )
            }
        } else if (state.categories.isNotEmpty()) {
            SectionLabel("Category")
            PickerRow(state.categories, state.categoryId, onSelect = { onEvent(TxEvent.SelectCategory(it)) })
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = { onEvent(TxEvent.SetTitle(it)) },
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
        )
        TitleSuggestions(state.titleSuggestions, onPick = { onEvent(TxEvent.SetTitle(it)) })

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldButton("Date", state.dateLabel, onClick = { datePicker = true }, modifier = Modifier.weight(1f))
            FieldButton("Time", state.timeLabel, onClick = { timePicker = true }, modifier = Modifier.weight(1f))
        }

        if (state.projects.isNotEmpty()) {
            SectionLabel("Project (optional)")
            PickerRow(state.projects, state.projectId, onSelect = { onEvent(TxEvent.SelectProject(it)) })
        }

        OutlinedTextField(
            value = state.description,
            onValueChange = { onEvent(TxEvent.SetDescription(it)) },
            label = { Text("Description (optional)") },
            minLines = 2,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 12.dp),
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp))
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp))
        }

        PrimaryButton(
            text = "Save",
            onClick = { onEvent(TxEvent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 8.dp),
        )
        if (state.type != TxType.Transfer) {
            QuietButton(
                text = "Save as a standard entry",
                onClick = { standardDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
            )
            Text(
                "A standard entry puts this category, amount and account behind one tap on the Money tab.",
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp),
            )
        }
    }

    if (datePicker) {
        CalendarSheet(
            title = "Date",
            selected = state.date,
            today = state.today,
            marks = CalendarMarks(emptySet(), emptyList()),
            weekStart = state.weekStart,
            onPick = {
                onEvent(TxEvent.SetDate(it))
                datePicker = false
            },
            onDismiss = { datePicker = false },
        )
    }
    if (timePicker) {
        TimeDialog(
            title = "Time",
            initial = state.time,
            now = java.time.LocalTime.now().withSecond(0).withNano(0),
            is24Hour = state.use24Hours,
            onDismiss = { timePicker = false },
            onSet = {
                onEvent(TxEvent.SetTime(it))
                timePicker = false
            },
        )
    }
    if (standardDialog) {
        var name by remember { mutableStateOf(state.title) }
        AlertDialog(
            onDismissRequest = { standardDialog = false },
            title = { Text("Name this standard entry") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    onEvent(TxEvent.SaveAsStandard(name))
                    standardDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { standardDialog = false }) { Text("Cancel") } },
        )
    }
}
