package com.zham.domain.usecase

import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Loan
import com.zham.domain.model.RateKind
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

/** One monthly payment of a repayment schedule, in minor units. */
data class Installment(
    val number: Int,
    val dueDate: LocalDate,
    val payment: Long,
    val principal: Long,
    val interest: Long,
    val balanceAfter: Long,
)

data class LoanSummary(
    val schedule: List<Installment>,
    val totalPayable: Long,
    val totalInterest: Long,
    /** The first monthly payment; for an annuity every payment but the last equals it. */
    val firstPayment: Long,
    val lastPayment: Long,
    val paidCount: Int,
    val paidAmount: Long,
    val remainingAmount: Long,
    /** Share of the payments already made, 0f..1f. */
    val paidFraction: Float,
    /** Share of the money to repay that has been paid, 0f..1f. */
    val paidMoneyFraction: Float,
    val nextDue: LocalDate?,
)

/**
 * The monthly rate. A nominal yearly rate is split into twelve, as banks quote it; an effective one
 * is the rate that compounds to the stated yearly figure, so it is a little smaller each month.
 */
fun monthlyRate(annualPercent: Double, kind: RateKind): Double {
    val yearly = annualPercent / 100.0
    return when (kind) {
        RateKind.Nominal -> yearly / 12.0
        RateKind.Effective -> (1.0 + yearly).pow(1.0 / 12.0) - 1.0
    }
}

private fun addMonths(start: LocalDate, months: Int): LocalDate = start.plusMonths(months.toLong())

/** The whole repayment schedule of [loan] from its first payment, with the paid part marked by [Loan.paidCount]. */
fun loanSummary(loan: Loan): LoanSummary {
    val n = loan.termMonths.coerceAtLeast(1)
    val r = monthlyRate(loan.annualRatePercent, loan.rateKind)
    val installments = mutableListOf<Installment>()
    var balance = loan.principalMinor

    val annuityPayment: Long = when {
        loan.type != AmortizationType.Annuity -> 0
        r == 0.0 -> (loan.principalMinor.toDouble() / n).roundToLong()
        else -> (loan.principalMinor * r / (1.0 - (1.0 + r).pow(-n.toDouble()))).roundToLong()
    }
    val evenPrincipal = loan.principalMinor / n

    for (k in 1..n) {
        val interest = (balance * r).roundToLong()
        val last = k == n
        val principal = when {
            last -> balance // the final payment clears whatever rounding left
            loan.type == AmortizationType.Annuity -> annuityPayment - interest
            else -> evenPrincipal
        }.coerceIn(0, balance)
        val payment = principal + interest
        balance -= principal
        installments += Installment(k, addMonths(loan.firstPaymentDate, k - 1), payment, principal, interest, balance)
    }

    val total = installments.sumOf { it.payment }
    val paid = loan.paidCount.coerceIn(0, n)
    val paidAmount = installments.take(paid).sumOf { it.payment }
    return LoanSummary(
        schedule = installments,
        totalPayable = total,
        totalInterest = installments.sumOf { it.interest },
        firstPayment = installments.first().payment,
        lastPayment = installments.last().payment,
        paidCount = paid,
        paidAmount = paidAmount,
        remainingAmount = total - paidAmount,
        paidFraction = paid.toFloat() / n,
        paidMoneyFraction = if (total == 0L) 0f else paidAmount.toFloat() / total,
        nextDue = installments.getOrNull(paid)?.dueDate,
    )
}
