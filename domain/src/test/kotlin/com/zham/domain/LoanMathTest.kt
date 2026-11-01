package com.zham.domain

import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Loan
import com.zham.domain.model.RateKind
import com.zham.domain.usecase.loanSummary
import com.zham.domain.usecase.monthlyRate
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import kotlin.test.Test

private fun loan(
    principal: Long = 30_000_000, rate: Double = 24.0, months: Int = 36,
    type: AmortizationType = AmortizationType.Annuity, kind: RateKind = RateKind.Nominal, paid: Int = 0,
) = Loan(1, "Car", "AMD", principal, rate, kind, months, type, LocalDate.of(2026, 1, 31), paid, null, null)

class LoanMathTest {
    @Test
    fun `the worked example gives the stated payment, total and interest`() {
        val s = loanSummary(loan())
        // 300 000 AMD, 36 months, 24% a year: about 11 769.86 a month.
        (s.firstPayment / 100.0) shouldBe (11_769.86).plusOrMinus(0.011)
        (s.totalPayable / 100.0) shouldBe (423_714.81).plusOrMinus(0.5)
        (s.totalInterest / 100.0) shouldBe (123_714.81).plusOrMinus(0.5)
        s.schedule.size shouldBe 36
        s.schedule.last().balanceAfter shouldBe 0
    }

    @Test
    fun `principal parts always add up to what was borrowed`() {
        listOf(AmortizationType.Annuity, AmortizationType.Differential).forEach { type ->
            val s = loanSummary(loan(principal = 1_234_567, months = 7, type = type))
            s.schedule.sumOf { it.principal } shouldBe 1_234_567
            s.schedule.zipWithNext().all { (a, b) -> b.balanceAfter <= a.balanceAfter } shouldBe true
        }
    }

    @Test
    fun `differential payments start high and fall`() {
        val s = loanSummary(loan(type = AmortizationType.Differential))
        s.firstPayment shouldBe 833_333 + 600_000
        (s.schedule.first().payment > s.schedule[10].payment) shouldBe true
        // Less interest overall than the same loan as an annuity.
        (s.totalInterest < loanSummary(loan()).totalInterest) shouldBe true
    }

    @Test
    fun `no interest means equal parts`() {
        val s = loanSummary(loan(principal = 1_200_000, rate = 0.0, months = 12))
        s.schedule.map { it.payment }.distinct() shouldBe listOf(100_000L)
        s.totalInterest shouldBe 0
    }

    @Test
    fun `an effective rate compounds to the stated yearly figure`() {
        val monthly = monthlyRate(26.82418, RateKind.Effective)
        (monthly) shouldBe (0.02).plusOrMinus(0.000001)
        monthlyRate(24.0, RateKind.Nominal) shouldBe (0.02).plusOrMinus(1e-12)
        // So the same stated rate costs less when it is effective.
        (loanSummary(loan(kind = RateKind.Effective)).totalInterest < loanSummary(loan()).totalInterest) shouldBe true
    }

    @Test
    fun `progress is counted in payments and in money`() {
        val s = loanSummary(loan(paid = 9))
        s.paidCount shouldBe 9
        s.paidFraction shouldBe 0.25f
        s.paidAmount shouldBe s.schedule.take(9).sumOf { it.payment }
        s.paidAmount + s.remainingAmount shouldBe s.totalPayable
        s.nextDue shouldBe LocalDate.of(2026, 10, 31)
        loanSummary(loan(paid = 36)).nextDue shouldBe null
    }

    @Test
    fun `due dates keep to the end of short months`() {
        val s = loanSummary(loan(months = 3))
        s.schedule.map { it.dueDate } shouldBe listOf(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
        )
    }
}
