package com.zham.app.money

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.CalendarSheet
import com.zham.app.ui.PickerRow
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.FieldButton
import com.zham.design.system.MeterBar
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.ScreenHeading
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme
import com.zham.domain.model.AmortizationType
import com.zham.domain.model.RateKind

@Composable
fun LoansScreen(onOpenLoan: (Long) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LoansViewModel = hiltViewModel()
    val state = viewModel.uiState()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Loans") {
                IconButton(onClick = { onOpenLoan(0) }) { Icon(Icons.Rounded.Add, contentDescription = "New loan") }
            }
            if (state.loans.isEmpty()) {
                EmptyNote("Enter what you owe now, the rate and the number of months. The monthly payment, the total and the schedule are worked out for you.")
            }
        }
        items(state.loans, key = { it.id }) { loan ->
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp), onClick = { onOpenLoan(loan.id) }) {
                Text(loan.name, style = MaterialTheme.typography.titleMedium)
                Text(loan.payment, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                MeterBar(fraction = loan.fraction, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(loan.progress, style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate)
                Text(loan.next, style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate)
            }
        }
    }
}

@Composable
fun LoanEditorScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LoanEditorViewModel = hiltViewModel()
    val state = viewModel.uiState()
    val onEvent = viewModel::onEvent
    var datePicker by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.finished) { if (viewModel.finished) onClose() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Text(if (state.existing) "Loan" else "New loan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (state.existing) {
                val askDelete = rememberDeleteConfirm("Delete this loan?", "Payments already recorded as expenses stay.") {
                    onEvent(LoanEditorEvent.Delete)
                }
                IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete loan") }
            }
        }

        val pad = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 6.dp)
        OutlinedTextField(state.name, { onEvent(LoanEditorEvent.SetName(it)) }, label = { Text("Name") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = pad)
        Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                state.principal, { onEvent(LoanEditorEvent.SetPrincipal(it)) }, label = { Text("Owed now") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                state.currency, { onEvent(LoanEditorEvent.SetCurrency(it)) }, label = { Text("Currency") }, singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
            )
        }
        Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                state.rate, { onEvent(LoanEditorEvent.SetRate(it)) }, label = { Text("Yearly rate, %") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                state.term, { onEvent(LoanEditorEvent.SetTerm(it)) }, label = { Text("Months left") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f),
            )
        }
        Segmented(
            options = RateKind.entries, selected = state.rateKind,
            label = { if (it == RateKind.Nominal) "Rate as stated" else "Effective rate" },
            onSelect = { onEvent(LoanEditorEvent.SetRateKind(it)) }, modifier = pad,
        )
        Text(
            if (state.rateKind == RateKind.Nominal) "The yearly rate is split into twelve equal months."
            else "The effective yearly rate compounds to the stated figure, so each month charges a little less.",
            style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate,
            modifier = Modifier.padding(horizontal = Spacing.Gutter),
        )
        Spacer(Modifier.height(6.dp))
        Segmented(
            options = AmortizationType.entries, selected = state.type,
            label = { if (it == AmortizationType.Annuity) "Equal payments" else "Falling payments" },
            onSelect = { onEvent(LoanEditorEvent.SetType(it)) }, modifier = pad,
        )
        Text(
            if (state.type == AmortizationType.Annuity) "Every month the same amount; interest is front-loaded."
            else "The same part of the debt is repaid every month, so payments start high and fall.",
            style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate,
            modifier = Modifier.padding(horizontal = Spacing.Gutter),
        )
        FieldButton("First payment", state.firstPaymentLabel, onClick = { datePicker = true }, modifier = pad)

        SectionLabel("Paid from")
        PickerRow(state.accounts, state.accountId, onSelect = { onEvent(LoanEditorEvent.SelectAccount(it)) })
        if (state.accountId != null && state.categories.isNotEmpty()) {
            SectionLabel("Category of the payments")
            PickerRow(state.categories, state.categoryId, onSelect = { onEvent(LoanEditorEvent.SelectCategory(it)) })
        }

        if (state.summaryLines.isNotEmpty()) {
            SectionLabel("Worked out")
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                state.summaryLines.forEachIndexed { index, line ->
                    Text(line, style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else null)
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp)) }

        PrimaryButton("Save", { onEvent(LoanEditorEvent.Save) }, enabled = state.valid, modifier = pad)
        if (state.summaryLines.isNotEmpty()) {
            Row(pad, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton(
                    if (state.accountId != null) "Mark payment ${state.paidCount + 1} paid" else "Mark next payment paid",
                    { onEvent(LoanEditorEvent.PayNext) }, Modifier.weight(1f),
                )
                if (state.paidCount > 0) QuietButton("Undo last", { onEvent(LoanEditorEvent.UndoPayment) }, Modifier.weight(1f))
            }
            if (state.accountId != null) {
                Text(
                    "Marking a payment paid also records it as an expense on the account.",
                    style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter),
                )
            }
            QuietButton(if (showSchedule) "Hide the schedule" else "Show the schedule", { showSchedule = !showSchedule }, pad)
            if (showSchedule) {
                state.schedule.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 4.dp)) {
                        Text("${row.number}", Modifier.weight(0.5f), color = if (row.paid) MaterialTheme.colorScheme.primary else ZhamTheme.extra.slate)
                        Column(Modifier.weight(2.2f)) {
                            Text(row.due, style = MaterialTheme.typography.bodySmall)
                            Text("interest ${row.interest}", style = MaterialTheme.typography.labelSmall, color = ZhamTheme.extra.slate)
                        }
                        Column(Modifier.weight(2f), horizontalAlignment = Alignment.End) {
                            Text(row.payment, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("left ${row.balance}", style = MaterialTheme.typography.labelSmall, color = ZhamTheme.extra.slate)
                        }
                    }
                }
            }
        }
    }

    if (datePicker) {
        CalendarSheet(
            title = "First payment",
            selected = state.firstPayment,
            today = state.today,
            marks = CalendarMarks(emptySet(), emptyList()),
            weekStart = state.weekStart,
            onPick = {
                onEvent(LoanEditorEvent.SetFirstPayment(it))
                datePicker = false
            },
            onDismiss = { datePicker = false },
        )
    }
}
