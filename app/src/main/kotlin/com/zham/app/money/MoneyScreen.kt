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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ChoiceChip
import com.zham.design.system.EmptyNote
import com.zham.design.system.GlyphIcon
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.ScreenHeading
import com.zham.design.system.SectionLabel
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme
import com.zham.domain.model.TxType

@Composable
fun MoneyScreen(
    onOpenTransaction: (id: Long, type: TxType) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MoneyViewModel = hiltViewModel()
    MoneyUi(viewModel.uiState(), viewModel::onEvent, onOpenTransaction, onOpenAccounts, modifier)
}

@Composable
fun MoneyUi(
    state: MoneyState,
    onEvent: (MoneyEvent) -> Unit,
    onOpenTransaction: (Long, TxType) -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Money") {
                IconButton(onClick = onOpenAccounts) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = "Accounts")
                }
            }

            if (!state.hasAccounts) {
                EmptyNote("Add an account to start. Open the wallet icon above.")
            } else {
                ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                    Text("Balance", style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                    state.balances.forEach {
                        Text(it, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.Gutter),
                ) {
                    item {
                        ChoiceChip(
                            label = "All accounts",
                            selected = state.selectedAccountId == null,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onEvent(MoneyEvent.SelectAccount(null)) },
                        )
                    }
                    items(state.accounts, key = { it.id }) { account ->
                        ChoiceChip(
                            label = account.name + " · " + account.balance,
                            selected = state.selectedAccountId == account.id,
                            color = account.color,
                            glyph = account.icon,
                            onClick = {
                                onEvent(MoneyEvent.SelectAccount(if (state.selectedAccountId == account.id) null else account.id))
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryButton("Expense", { onOpenTransaction(0, TxType.Expense) }, Modifier.weight(1f))
                    QuietButton("Income", { onOpenTransaction(0, TxType.Income) }, Modifier.weight(1f))
                    QuietButton("Transfer", { onOpenTransaction(0, TxType.Transfer) }, Modifier.weight(1f))
                }

                if (state.presets.isNotEmpty()) {
                    SectionLabel("Standard")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
                    ) {
                        items(state.presets, key = { it.id }) { preset ->
                            ChoiceChip(
                                label = preset.name + " · " + preset.amount,
                                selected = false,
                                color = preset.color,
                                onClick = { onEvent(MoneyEvent.ApplyPreset(preset.id)) },
                            )
                        }
                    }
                }

                state.message?.let { message ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        if (state.canUndo) TextButton(onClick = { onEvent(MoneyEvent.Undo) }) { Text("Undo") }
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp))
                }

                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onEvent(MoneyEvent.ShiftMonth(-1)) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
                        if (state.monthIncome.isNotEmpty() || state.monthExpense.isNotEmpty()) {
                            Text(
                                (state.monthIncome.map { "+$it" } + state.monthExpense.map { "−$it" }).joinToString("   "),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhamTheme.extra.slate,
                            )
                        }
                    }
                    IconButton(onClick = { onEvent(MoneyEvent.ShiftMonth(1)) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }
                if (state.days.isEmpty()) EmptyNote("Nothing recorded this month.")
            }
        }
        state.days.forEach { day ->
            item(key = "day-" + day.title) { SectionLabel(day.title) }
            items(day.rows, key = { "tx-" + it.id }) { row ->
                ZhamCard(
                    Modifier.padding(horizontal = Spacing.Gutter, vertical = 4.dp),
                    onClick = { onOpenTransaction(row.id, row.type) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.icon?.let { GlyphIcon(it, tint = row.color, size = 24.dp) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            Text(row.detail, style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate)
                        }
                        Text(
                            row.amount,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (row.type == TxType.Income) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
