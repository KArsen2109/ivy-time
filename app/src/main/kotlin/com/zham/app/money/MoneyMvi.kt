package com.zham.app.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.TimeFormatters
import com.zham.app.ui.message
import com.zham.data.SettingsStore
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.PresetKind
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.TxType
import com.zham.domain.usecase.ApplyPresetUseCase
import com.zham.domain.usecase.accountBalance
import com.zham.domain.usecase.balancesByCurrency
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.periodSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class AccountChip(val id: Long, val name: String, val balance: String, val color: Color, val icon: String)

@Immutable
data class TxRow(
    val id: Long,
    val title: String,
    val detail: String,
    val amount: String,
    val type: TxType,
    val color: Color,
    val icon: String?,
)

@Immutable
data class TxDay(val title: String, val rows: ImmutableList<TxRow>)

@Immutable
data class PresetChip(val id: Long, val name: String, val amount: String, val color: Color)

@Immutable
data class MoneyState(
    val balances: ImmutableList<String>,
    val accounts: ImmutableList<AccountChip>,
    val selectedAccountId: Long?,
    val monthLabel: String,
    val monthIncome: ImmutableList<String>,
    val monthExpense: ImmutableList<String>,
    val days: ImmutableList<TxDay>,
    val presets: ImmutableList<PresetChip>,
    val hasAccounts: Boolean,
    val message: String?,
    val canUndo: Boolean,
    val error: String?,
)

sealed interface MoneyEvent {
    data class SelectAccount(val id: Long?) : MoneyEvent
    data class ShiftMonth(val months: Long) : MoneyEvent
    data class ApplyPreset(val id: Long) : MoneyEvent
    data object Undo : MoneyEvent
    data object DismissMessage : MoneyEvent
}

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: MoneyCategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val presetRepository: PresetRepository,
    private val settingsStore: SettingsStore,
    private val applyPreset: ApplyPresetUseCase,
    private val clock: Clock,
) : ComposeViewModel<MoneyState, MoneyEvent>() {

    private val selected = mutableStateOf<Long?>(null)
    private val monthOffset = mutableLongStateOf(0L)
    private val message = mutableStateOf<String?>(null)
    private val error = mutableStateOf<String?>(null)
    private var lastAdded: Long? = null
    private var hideMessage: Job? = null

    @Composable
    override fun uiState(): MoneyState {
        val accounts by remember { accountRepository.observeAccounts() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val transactions by remember { transactionRepository.observeTransactions() }.collectAsState(emptyList())
        val presets by remember { presetRepository.observePresets() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)

        val zone = clock.zone
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)
        val today = LocalDate.now(clock)
        val month = YearMonth.from(today).plusMonths(monthOffset.longValue)
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        val filter = selected.value?.takeIf { accountsById.containsKey(it) }

        val inMonth = transactions.filter { tx ->
            val day = tx.time.atZone(zone).toLocalDate()
            !day.isBefore(from) && !day.isAfter(to) &&
                (filter == null || tx.accountId == filter || tx.toAccountId == filter)
        }
        val summary = periodSummary(inMonth, accounts, from, to, zone)

        val days = inMonth.groupBy { it.time.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) ->
                TxDay(
                    title = when (day) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> formatters.shortDate(day)
                    },
                    rows = list.sortedByDescending { it.time }.map { tx ->
                        val account = accountsById[tx.accountId]
                        val category = categoriesById[tx.categoryId]
                        val code = account?.currency.orEmpty()
                        TxRow(
                            id = tx.id,
                            title = tx.title.ifBlank {
                                when (tx.type) {
                                    TxType.Transfer -> "Transfer"
                                    else -> category?.name ?: if (tx.type == TxType.Income) "Income" else "Expense"
                                }
                            },
                            detail = listOfNotNull(
                                category?.name?.takeIf { tx.type != TxType.Transfer },
                                if (tx.type == TxType.Transfer) {
                                    (account?.name ?: "?") + " → " + (accountsById[tx.toAccountId]?.name ?: "?")
                                } else {
                                    account?.name
                                },
                                formatters.time(tx.time),
                            ).joinToString(" · "),
                            amount = when (tx.type) {
                                TxType.Income -> "+" + formatMinor(tx.amountMinor, code)
                                TxType.Expense -> "−" + formatMinor(tx.amountMinor, code)
                                TxType.Transfer -> formatMinor(tx.amountMinor, code)
                            },
                            type = tx.type,
                            color = Color(category?.color ?: account?.color ?: 0xFF7A8CA8.toInt()),
                            icon = category?.icon ?: account?.icon,
                        )
                    }.toImmutableList(),
                )
            }.toImmutableList()

        return MoneyState(
            balances = balancesByCurrency(accounts, transactions)
                .map { (code, minor) -> formatMinor(minor, code) }.toImmutableList(),
            accounts = accounts.filter { !it.archived }.map {
                AccountChip(it.id, it.name, formatMinor(accountBalance(it, transactions), it.currency), Color(it.color), it.icon)
            }.toImmutableList(),
            selectedAccountId = filter,
            monthLabel = formatters.monthYear(month),
            monthIncome = summary.income.map { (c, v) -> formatMinor(v, c) }.toImmutableList(),
            monthExpense = summary.expense.map { (c, v) -> formatMinor(v, c) }.toImmutableList(),
            days = days,
            presets = presets.filter { it.kind != PresetKind.Activity }.map {
                PresetChip(
                    it.id, it.name,
                    formatMinor(it.amountMinor),
                    Color(categoriesById[it.categoryId]?.color ?: 0xFF7A8CA8.toInt()),
                )
            }.toImmutableList(),
            hasAccounts = accounts.any { !it.archived },
            message = message.value,
            canUndo = lastAdded != null,
            error = error.value,
        )
    }

    override fun onEvent(event: MoneyEvent) {
        when (event) {
            is MoneyEvent.SelectAccount -> selected.value = event.id
            is MoneyEvent.ShiftMonth -> monthOffset.longValue += event.months
            is MoneyEvent.ApplyPreset -> viewModelScope.launch {
                applyPreset(event.id).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = {
                        error.value = null
                        lastAdded = it.transactionId
                        show("Added " + it.description)
                    },
                )
            }
            MoneyEvent.Undo -> viewModelScope.launch {
                lastAdded?.let { transactionRepository.delete(it) }
                lastAdded = null
                message.value = null
            }
            MoneyEvent.DismissMessage -> {
                message.value = null
                lastAdded = null
            }
        }
    }

    private fun show(text: String) {
        message.value = text
        hideMessage?.cancel()
        hideMessage = viewModelScope.launch {
            delay(6_000)
            message.value = null
            lastAdded = null
        }
    }
}
