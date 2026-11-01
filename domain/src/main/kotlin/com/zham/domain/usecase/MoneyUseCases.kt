package com.zham.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.Loan
import com.zham.domain.model.LoanRepository
import com.zham.domain.model.PresetKind
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.TimeError
import com.zham.domain.model.TimeRepository
import com.zham.domain.model.Transaction
import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.TxType
import kotlinx.coroutines.flow.firstOrNull
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** What tapping a preset did, in words for a short message. */
data class Applied(val description: String, val transactionId: Long?)

/**
 * Makes the record a preset describes, dated now. A money preset becomes a transaction on its
 * account (or the first one, if it has none); an activity becomes a time record that ends now
 * and lasts as long as the preset says.
 */
class ApplyPresetUseCase @Inject constructor(
    private val presets: PresetRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val time: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(presetId: Long): Either<TimeError, Applied> {
        val preset = presets.findById(presetId) ?: return TimeError.PresetNotFound.left()
        val now = clock.instant()
        return when (preset.kind) {
            PresetKind.Activity -> {
                val minutes = preset.durationMinutes.coerceAtLeast(1)
                SaveEntryUseCase(time, clock)(
                    id = 0,
                    categoryId = preset.categoryId,
                    projectId = preset.projectId,
                    note = preset.title,
                    start = now.minus(Duration.ofMinutes(minutes.toLong())),
                    end = now,
                ).map { Applied(preset.name + " " + Duration.ofMinutes(minutes.toLong()).formatShort(), null) }
            }
            PresetKind.Expense, PresetKind.Income -> {
                if (preset.amountMinor <= 0) return TimeError.InvalidAmount.left()
                val list = accounts.observeAccounts().firstOrNull().orEmpty().filter { !it.archived }
                val account = list.firstOrNull { it.id == preset.accountId } ?: list.firstOrNull()
                    ?: return TimeError.NoAccount.left()
                val id = transactions.save(
                    Transaction(
                        id = 0,
                        type = if (preset.kind == PresetKind.Expense) TxType.Expense else TxType.Income,
                        amountMinor = preset.amountMinor,
                        accountId = account.id,
                        toAccountId = null,
                        toAmountMinor = null,
                        categoryId = preset.categoryId,
                        title = preset.title,
                        description = "",
                        time = now,
                        projectId = preset.projectId,
                    )
                )
                Applied(preset.name + " " + formatMinor(preset.amountMinor, account.currency), id).right()
            }
        }
    }
}

/** Checks a loan before it is saved and keeps the paid count within the term. */
class SaveLoanUseCase @Inject constructor(private val loans: LoanRepository) {
    suspend operator fun invoke(loan: Loan): Either<TimeError, Long> {
        if (loan.name.isBlank()) return TimeError.BlankName.left()
        if (loan.principalMinor <= 0 || loan.termMonths < 1 || loan.annualRatePercent < 0) {
            return TimeError.InvalidLoan.left()
        }
        return loans.save(
            loan.copy(name = loan.name.trim(), paidCount = loan.paidCount.coerceIn(0, loan.termMonths))
        ).right()
    }
}

/**
 * Marks the next payment of a loan as made and, when the loan has an account, records it there as
 * an expense on the day it is made. [undo] takes the last payment back, with its expense.
 */
class PayLoanUseCase @Inject constructor(
    private val loans: LoanRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) {
    suspend fun pay(loanId: Long): Either<TimeError, Unit> {
        val loan = loans.findById(loanId) ?: return TimeError.InvalidLoan.left()
        val summary = loanSummary(loan)
        val installment = summary.schedule.getOrNull(loan.paidCount) ?: return TimeError.InvalidLoan.left()
        if (loan.accountId != null) {
            transactions.save(
                Transaction(
                    id = 0,
                    type = TxType.Expense,
                    amountMinor = installment.payment,
                    accountId = loan.accountId,
                    toAccountId = null,
                    toAmountMinor = null,
                    categoryId = loan.categoryId,
                    title = loan.name + " " + installment.number + "/" + loan.termMonths,
                    description = "",
                    time = clock.instant(),
                    projectId = null,
                )
            )
        }
        loans.save(loan.copy(paidCount = loan.paidCount + 1))
        return Unit.right()
    }

    suspend fun undo(loanId: Long): Either<TimeError, Unit> {
        val loan = loans.findById(loanId) ?: return TimeError.InvalidLoan.left()
        if (loan.paidCount <= 0) return Unit.right()
        val number = loan.paidCount
        if (loan.accountId != null) {
            val title = loan.name + " " + number + "/" + loan.termMonths
            transactions.observeTransactions().firstOrNull().orEmpty()
                .filter { it.accountId == loan.accountId && it.title == title }
                .maxByOrNull { it.time }
                ?.let { transactions.delete(it.id) }
        }
        loans.save(loan.copy(paidCount = number - 1))
        return Unit.right()
    }
}
