package com.zham.app.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.TimeFormatters
import com.zham.data.SettingsStore
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.Preset
import com.zham.domain.model.PresetKind
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.Transaction
import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.TxType
import com.zham.domain.usecase.accountBalance
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.parseAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@Immutable
data class TxState(
    val editing: Boolean,
    val type: TxType,
    val amount: String,
    val toAmount: String,
    val needsToAmount: Boolean,
    val accounts: ImmutableList<PickerItem>,
    val accountId: Long?,
    val toAccountId: Long?,
    val currency: String,
    val toCurrency: String,
    val categories: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val projects: ImmutableList<PickerItem>,
    val projectId: Long?,
    val title: String,
    val description: String,
    val titleSuggestions: ImmutableList<String>,
    val date: LocalDate,
    val time: LocalTime,
    val dateLabel: String,
    val timeLabel: String,
    val use24Hours: Boolean,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val canSave: Boolean,
    val error: String?,
    val message: String?,
)

sealed interface TxEvent {
    data class SetType(val type: TxType) : TxEvent
    data class SetAmount(val text: String) : TxEvent
    data class SetToAmount(val text: String) : TxEvent
    data class SelectAccount(val id: Long?) : TxEvent
    data class SelectToAccount(val id: Long?) : TxEvent
    data class SelectCategory(val id: Long?) : TxEvent
    data class SelectProject(val id: Long?) : TxEvent
    data class SetTitle(val text: String) : TxEvent
    data class SetDescription(val text: String) : TxEvent
    data class SetDate(val date: LocalDate) : TxEvent
    data class SetTime(val time: LocalTime) : TxEvent
    data class SaveAsStandard(val name: String) : TxEvent
    data object Save : TxEvent
    data object Delete : TxEvent
}

@HiltViewModel
class TxViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: MoneyCategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val presetRepository: PresetRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ComposeViewModel<TxState, TxEvent>() {

    private val txId: Long = savedStateHandle.get<String>("txId")?.toLongOrNull() ?: 0L

    private val type = mutableStateOf(
        savedStateHandle.get<String>("type")?.let { name -> TxType.entries.firstOrNull { it.name == name } }
            ?: TxType.Expense
    )
    private val amount = mutableStateOf("")
    private val toAmount = mutableStateOf("")
    private val accountId = mutableStateOf<Long?>(null)
    private val toAccountId = mutableStateOf<Long?>(null)
    private val categoryId = mutableStateOf<Long?>(null)
    private val projectId = mutableStateOf<Long?>(null)
    private val title = mutableStateOf("")
    private val description = mutableStateOf("")
    private val date = mutableStateOf(LocalDate.now(clock))
    private val time = mutableStateOf(LocalTime.now(clock).withSecond(0).withNano(0))
    private val error = mutableStateOf<String?>(null)
    private val message = mutableStateOf<String?>(null)
    private val closed = mutableStateOf(false)
    private var saving = false

    val finished: Boolean get() = closed.value

    init {
        viewModelScope.launch {
            if (txId != 0L) {
                transactionRepository.findById(txId)?.let { tx ->
                    type.value = tx.type
                    amount.value = plain(tx.amountMinor)
                    toAmount.value = tx.toAmountMinor?.let(::plain).orEmpty()
                    accountId.value = tx.accountId
                    toAccountId.value = tx.toAccountId
                    categoryId.value = tx.categoryId
                    projectId.value = tx.projectId
                    title.value = tx.title
                    description.value = tx.description
                    val local = tx.time.atZone(clock.zone)
                    date.value = local.toLocalDate()
                    time.value = local.toLocalTime().withSecond(0).withNano(0)
                }
            } else {
                // A new entry starts on the account used last, which is nearly always the right one.
                val last = transactionRepository.observeTransactions().first().firstOrNull()?.accountId
                val accounts = accountRepository.observeAccounts().first().filter { !it.archived }
                accountId.value = accounts.firstOrNull { it.id == last }?.id ?: accounts.firstOrNull()?.id
            }
        }
    }

    @Composable
    override fun uiState(): TxState {
        val accounts by remember { accountRepository.observeAccounts() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val transactions by remember { transactionRepository.observeTransactions() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val formatters = TimeFormatters(clock.zone, settings?.use24Hours ?: true)

        val usable = accounts.filter { !it.archived || it.id == accountId.value }
        val from = accounts.firstOrNull { it.id == accountId.value }
        val to = accounts.firstOrNull { it.id == toAccountId.value }
        val needsToAmount = type.value == TxType.Transfer && from != null && to != null && from.currency != to.currency

        val suggestions = remember(transactions, categoryId.value, title.value, type.value) {
            val typed = title.value.trim().lowercase()
            transactions
                .filter { it.title.isNotBlank() && it.type == type.value &&
                    (categoryId.value == null || it.categoryId == categoryId.value) }
                .groupBy { it.title.trim() }
                .entries.sortedByDescending { it.value.size }
                .map { it.key }
                .filter { typed.isEmpty() || (it.lowercase().contains(typed) && it.lowercase() != typed) }
                .take(10).toImmutableList()
        }

        return TxState(
            editing = txId != 0L,
            type = type.value,
            amount = amount.value,
            toAmount = toAmount.value,
            needsToAmount = needsToAmount,
            accounts = usable.map {
                PickerItem(it.id, it.name + " · " + formatMinor(accountBalance(it, transactions), it.currency), Color(it.color), it.icon)
            }.toImmutableList(),
            accountId = accountId.value,
            toAccountId = toAccountId.value,
            currency = from?.currency.orEmpty(),
            toCurrency = to?.currency.orEmpty(),
            categories = categories.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            categoryId = categoryId.value,
            projects = projects.filter { !it.archived }
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon.ifBlank { null }) }.toImmutableList(),
            projectId = projectId.value,
            title = title.value,
            description = description.value,
            titleSuggestions = suggestions,
            date = date.value,
            time = time.value,
            dateLabel = formatters.shortDate(date.value),
            timeLabel = formatters.time(time.value),
            use24Hours = settings?.use24Hours ?: true,
            today = LocalDate.now(clock),
            weekStart = settings?.weekStart ?: DayOfWeek.MONDAY,
            canSave = (parseAmount(amount.value) ?: 0) > 0 && accountId.value != null &&
                (type.value != TxType.Transfer || (toAccountId.value != null && toAccountId.value != accountId.value &&
                    (!needsToAmount || (parseAmount(toAmount.value) ?: 0) > 0))),
            error = error.value,
            message = message.value,
        )
    }

    override fun onEvent(event: TxEvent) {
        error.value = null
        when (event) {
            is TxEvent.SetType -> type.value = event.type
            is TxEvent.SetAmount -> amount.value = event.text.filter { it.isDigit() || it in ".,  " }.take(16)
            is TxEvent.SetToAmount -> toAmount.value = event.text.filter { it.isDigit() || it in ".,  " }.take(16)
            is TxEvent.SelectAccount -> accountId.value = event.id
            is TxEvent.SelectToAccount -> toAccountId.value = event.id
            is TxEvent.SelectCategory -> categoryId.value = event.id
            is TxEvent.SelectProject -> projectId.value = event.id
            is TxEvent.SetTitle -> title.value = event.text
            is TxEvent.SetDescription -> description.value = event.text
            is TxEvent.SetDate -> date.value = event.date
            is TxEvent.SetTime -> time.value = event.time
            is TxEvent.SaveAsStandard -> viewModelScope.launch {
                val minor = parseAmount(amount.value) ?: 0
                presetRepository.save(
                    Preset(
                        id = 0,
                        kind = if (type.value == TxType.Income) PresetKind.Income else PresetKind.Expense,
                        name = event.name.trim().ifBlank { title.value.ifBlank { "Standard" } },
                        title = title.value.trim(),
                        categoryId = categoryId.value,
                        projectId = projectId.value,
                        accountId = accountId.value,
                        amountMinor = minor,
                        durationMinutes = 0,
                        orderNum = System.currentTimeMillis().toDouble(),
                    )
                )
                message.value = "Saved as a standard entry. Find it on the Money tab."
            }
            TxEvent.Save -> save()
            TxEvent.Delete -> viewModelScope.launch {
                if (txId != 0L) transactionRepository.delete(txId)
                closed.value = true
            }
        }
    }

    private fun save() {
        if (saving) return
        val minor = parseAmount(amount.value)?.takeIf { it > 0 }
        val account = accountId.value
        if (minor == null || account == null) {
            error.value = "Enter an amount above zero."
            return
        }
        saving = true
        viewModelScope.launch {
            val isTransfer = type.value == TxType.Transfer
            val toMinor = if (isTransfer) parseAmount(toAmount.value)?.takeIf { it > 0 } ?: minor else null
            val at = date.value.atTime(time.value).atZone(clock.zone).toInstant()
            transactionRepository.save(
                Transaction(
                    id = txId,
                    type = type.value,
                    amountMinor = minor,
                    accountId = account,
                    toAccountId = toAccountId.value.takeIf { isTransfer },
                    toAmountMinor = toMinor,
                    categoryId = categoryId.value.takeIf { !isTransfer },
                    title = title.value.trim(),
                    description = description.value.trim(),
                    time = at,
                    projectId = projectId.value,
                )
            )
            closed.value = true
        }
    }

    /** An amount in minor units as the text it is edited as. */
    private fun plain(minor: Long): String = formatMinor(minor).replace(" ", "")
}
