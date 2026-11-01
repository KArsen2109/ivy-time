package com.zham.domain

import com.zham.domain.model.Account
import com.zham.domain.model.Transaction
import com.zham.domain.model.TxType
import com.zham.domain.usecase.accountBalance
import com.zham.domain.usecase.balancesByCurrency
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.parseAmount
import com.zham.domain.usecase.periodSummary
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test

private val cash = Account(1, "Cash", "AMD", 0, "money", 100_000, 0.0)
private val card = Account(2, "Card", "AMD", 0, "money", 0, 1.0)
private val usd = Account(3, "Dollars", "USD", 0, "money", 5_000, 2.0)
private val T0 = Instant.parse("2026-01-10T10:00:00Z")

private fun tx(type: TxType, amount: Long, account: Long, to: Long? = null, toAmount: Long? = null, time: Instant = T0) =
    Transaction(0, type, amount, account, to, toAmount, null, "", "", time, null)

class MoneyTest {
    @Test
    fun `income raises and expense lowers an account`() {
        val list = listOf(tx(TxType.Income, 50_000, 1), tx(TxType.Expense, 30_000, 1), tx(TxType.Expense, 9_999, 2))
        accountBalance(cash, list) shouldBe 120_000
        accountBalance(card, list) shouldBe -9_999
    }

    @Test
    fun `a transfer moves money and a converted one arrives in the other amount`() {
        val list = listOf(tx(TxType.Transfer, 40_000, 1, to = 2), tx(TxType.Transfer, 10_000, 1, to = 3, toAmount = 25))
        accountBalance(cash, list) shouldBe 50_000
        accountBalance(card, list) shouldBe 40_000
        accountBalance(usd, list) shouldBe 5_025
    }

    @Test
    fun `balances are never added across currencies`() {
        balancesByCurrency(listOf(cash, card, usd), listOf(tx(TxType.Income, 1_000, 3))) shouldBe
            mapOf("AMD" to 100_000L, "USD" to 6_000L)
    }

    @Test
    fun `a period summary counts income and spending but not transfers`() {
        val list = listOf(
            tx(TxType.Income, 500, 1), tx(TxType.Expense, 200, 1), tx(TxType.Transfer, 300, 1, to = 2),
            tx(TxType.Expense, 999, 1, time = Instant.parse("2026-03-01T10:00:00Z")),
        )
        val s = periodSummary(list, listOf(cash, card), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), ZoneOffset.UTC)
        s.income shouldBe mapOf("AMD" to 500L)
        s.expense shouldBe mapOf("AMD" to 200L)
    }

    @Test
    fun `typed amounts are read into minor units`() {
        parseAmount("300") shouldBe 30_000L
        parseAmount("1 250,5") shouldBe 125_050L
        parseAmount("12.34") shouldBe 1_234L
        parseAmount(".5") shouldBe 50L
        parseAmount("12.345") shouldBe null
        parseAmount("abc") shouldBe null
        parseAmount("") shouldBe null
        parseAmount("1.2.3") shouldBe null
    }

    @Test
    fun `amounts are written with spaces and cents only when there are any`() {
        formatMinor(125_050) shouldBe "1 250.50"
        formatMinor(30_000, "AMD") shouldBe "300 AMD"
        formatMinor(-100_000) shouldBe "-1 000"
        formatMinor(5) shouldBe "0.05"
    }
}
