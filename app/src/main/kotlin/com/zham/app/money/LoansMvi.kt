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
import com.zham.app.ui.message
import com.zham.data.SettingsStore
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Loan
import com.zham.domain.model.LoanRepository
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.RateKind
import com.zham.domain.usecase.PayLoanUseCase
import com.zham.domain.usecase.SaveLoanUseCase
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.loanSummary
import com.zham.domain.usecase.parseAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class LoanRow(
    val id: Long,
    val name: String,
    val payment: String,
    val progress: String,
    val fraction: Float,
    val next: String,
)

@Immutable
data class LoansState(val loans: ImmutableList<LoanRow>)

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ComposeViewModel<LoansState, Unit>() {
    @Composable
    override fun uiState(): LoansState {
        val loans by remember { loanRepository.observeLoans() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val formatters = TimeFormatters(clock.zone, settings?.use24Hours ?: true)
        return LoansState(
            loans.map { loan ->
                val s = loanSummary(loan)
                LoanRow(
                    loan.id, loan.name, formatMinor(s.firstPayment, loan.currency) + " a month",
                    "Paid ${s.paidCount} of ${loan.termMonths} payments (${(s.paidFraction * 100).toInt()}%)",
                    s.paidFraction,
                    s.nextDue?.let { "Next: " + formatters.shortDate(it) } ?: "Fully repaid",
                )
            }.toImmutableList()
        )
    }

    override fun onEvent(event: Unit) = Unit
}

@Immutable
data class LoanEditorState(
    val existing: Boolean,
    val name: String,
    val currency: String,
    val principal: String,
    val rate: String,
    val rateKind: RateKind,
    val term: String,
    val type: AmortizationType,
    val firstPayment: LocalDate,
    val firstPaymentLabel: String,
    val accounts: ImmutableList<PickerItem>,
    val accountId: Long?,
    val categories: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val paidCount: Int,
    val summaryLines: ImmutableList<String>,
    val schedule: ImmutableList<ScheduleRow>,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val valid: Boolean,
    val error: String?,
)

@Immutable
data class ScheduleRow(val number: Int, val due: String, val payment: String, val interest: String, val balance: String, val paid: Boolean)

sealed interface LoanEditorEvent {
    data class SetName(val v: String) : LoanEditorEvent
    data class SetCurrency(val v: String) : LoanEditorEvent
    data class SetPrincipal(val v: String) : LoanEditorEvent
    data class SetRate(val v: String) : LoanEditorEvent
    data class SetRateKind(val v: RateKind) : LoanEditorEvent
    data class SetTerm(val v: String) : LoanEditorEvent
    data class SetType(val v: AmortizationType) : LoanEditorEvent
    data class SetFirstPayment(val v: LocalDate) : LoanEditorEvent
    data class SelectAccount(val id: Long?) : LoanEditorEvent
    data class SelectCategory(val id: Long?) : LoanEditorEvent
    data object Save : LoanEditorEvent
    data object PayNext : LoanEditorEvent
    data object UndoPayment : LoanEditorEvent
    data object Delete : LoanEditorEvent
}

@HiltViewModel
class LoanEditorViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: MoneyCategoryRepository,
    private val settingsStore: SettingsStore,
    private val saveLoan: SaveLoanUseCase,
    private val payLoan: PayLoanUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ComposeViewModel<LoanEditorState, LoanEditorEvent>() {

    private var loanId: Long = savedStateHandle.get<String>("loanId")?.toLongOrNull() ?: 0L
    private val name = mutableStateOf("")
    private val currency = mutableStateOf("")
    private val principal = mutableStateOf("")
    private val rate = mutableStateOf("")
    private val rateKind = mutableStateOf(RateKind.Nominal)
    private val term = mutableStateOf("")
    private val type = mutableStateOf(AmortizationType.Annuity)
    private val firstPayment = mutableStateOf(LocalDate.now(clock).plusMonths(1))
    private val accountId = mutableStateOf<Long?>(null)
    private val categoryId = mutableStateOf<Long?>(null)
    private val paidCount = mutableStateOf(0)
    private val error = mutableStateOf<String?>(null)
    private val closed = mutableStateOf(false)

    val finished: Boolean get() = closed.value

    init {
        if (loanId != 0L) {
            viewModelScope.launch { loanRepository.findById(loanId)?.let(::load) }
        }
    }

    private fun load(loan: Loan) {
        name.value = loan.name
        currency.value = loan.currency
        principal.value = formatMinor(loan.principalMinor).replace(" ", "")
        rate.value = loan.annualRatePercent.toString().removeSuffix(".0")
        rateKind.value = loan.rateKind
        term.value = loan.termMonths.toString()
        type.value = loan.type
        firstPayment.value = loan.firstPaymentDate
        accountId.value = loan.accountId
        categoryId.value = loan.categoryId
        paidCount.value = loan.paidCount
    }

    private fun current(): Loan? {
        val principalMinor = parseAmount(principal.value)?.takeIf { it > 0 } ?: return null
        val months = term.value.toIntOrNull()?.takeIf { it in 1..600 } ?: return null
        val percent = rate.value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..1000.0 } ?: return null
        return Loan(
            loanId, name.value, currency.value.ifBlank { "AMD" }.uppercase(), principalMinor, percent, rateKind.value,
            months, type.value, firstPayment.value, paidCount.value, accountId.value, categoryId.value,
        )
    }

    @Composable
    override fun uiState(): LoanEditorState {
        val accounts by remember { accountRepository.observeAccounts() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val formatters = TimeFormatters(clock.zone, settings?.use24Hours ?: true)
        val loan = current()
        val summary = loan?.let(::loanSummary)
        val code = loan?.currency ?: currency.value

        return LoanEditorState(
            existing = loanId != 0L,
            name = name.value,
            currency = currency.value,
            principal = principal.value,
            rate = rate.value,
            rateKind = rateKind.value,
            term = term.value,
            type = type.value,
            firstPayment = firstPayment.value,
            firstPaymentLabel = formatters.shortDate(firstPayment.value),
            accounts = accounts.filter { !it.archived }.map { PickerItem(it.id, it.name + " · " + it.currency, Color(it.color), it.icon) }.toImmutableList(),
            accountId = accountId.value,
            categories = categories.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            categoryId = categoryId.value,
            paidCount = paidCount.value,
            summaryLines = if (summary == null) {
                emptyList<String>().toImmutableList()
            } else {
                listOf(
                    if (type.value == AmortizationType.Annuity) {
                        "Monthly payment: " + formatMinor(summary.firstPayment, code)
                    } else {
                        "First payment: " + formatMinor(summary.firstPayment, code) +
                            ", last: " + formatMinor(summary.lastPayment, code)
                    },
                    "Total to pay: " + formatMinor(summary.totalPayable, code),
                    "Of which interest: " + formatMinor(summary.totalInterest, code),
                    "Paid so far: " + formatMinor(summary.paidAmount, code) + " (${summary.paidCount} of ${loan.termMonths}, " +
                        "${(summary.paidMoneyFraction * 100).toInt()}% of the money)",
                    "Left to pay: " + formatMinor(summary.remainingAmount, code),
                ).toImmutableList()
            },
            schedule = summary?.schedule?.map {
                ScheduleRow(
                    it.number, formatters.shortDate(it.dueDate), formatMinor(it.payment), formatMinor(it.interest),
                    formatMinor(it.balanceAfter), it.number <= summary.paidCount,
                )
            }.orEmpty().toImmutableList(),
            today = LocalDate.now(clock),
            weekStart = settings?.weekStart ?: DayOfWeek.MONDAY,
            valid = loan != null && name.value.isNotBlank(),
            error = error.value,
        )
    }

    override fun onEvent(event: LoanEditorEvent) {
        error.value = null
        when (event) {
            is LoanEditorEvent.SetName -> name.value = event.v
            is LoanEditorEvent.SetCurrency -> currency.value = event.v.take(3).uppercase()
            is LoanEditorEvent.SetPrincipal -> principal.value = event.v.filter { it.isDigit() || it in ".," }.take(16)
            is LoanEditorEvent.SetRate -> rate.value = event.v.filter { it.isDigit() || it in ".," }.take(7)
            is LoanEditorEvent.SetRateKind -> rateKind.value = event.v
            is LoanEditorEvent.SetTerm -> term.value = event.v.filter(Char::isDigit).take(3)
            is LoanEditorEvent.SetType -> type.value = event.v
            is LoanEditorEvent.SetFirstPayment -> firstPayment.value = event.v
            is LoanEditorEvent.SelectAccount -> accountId.value = event.id
            is LoanEditorEvent.SelectCategory -> categoryId.value = event.id
            LoanEditorEvent.Save -> viewModelScope.launch {
                val loan = current()
                if (loan == null) {
                    error.value = "Fill in the amount, the rate and the term."
                    return@launch
                }
                saveLoan(loan).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { closed.value = true },
                )
            }
            LoanEditorEvent.PayNext -> viewModelScope.launch {
                val loan = current() ?: return@launch
                // The loan is saved first so the payment is counted against what is on screen.
                saveLoan(loan).fold({ error.value = it.message() }) { id ->
                    loanId = id
                    payLoan.pay(id).fold({ error.value = it.message() }) {
                        loanRepository.findById(id)?.let { paidCount.value = it.paidCount }
                    }
                }
            }
            LoanEditorEvent.UndoPayment -> viewModelScope.launch {
                if (loanId == 0L) return@launch
                payLoan.undo(loanId).fold({ error.value = it.message() }) {
                    loanRepository.findById(loanId)?.let { paidCount.value = it.paidCount }
                }
            }
            LoanEditorEvent.Delete -> viewModelScope.launch {
                if (loanId != 0L) loanRepository.delete(loanId)
                closed.value = true
            }
        }
    }
}
