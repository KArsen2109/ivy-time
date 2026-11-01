package com.zham.domain

import com.zham.domain.model.Account
import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Loan
import com.zham.domain.model.Preset
import com.zham.domain.model.PresetKind
import com.zham.domain.model.RateKind
import com.zham.domain.model.TxType
import com.zham.domain.usecase.ApplyPresetUseCase
import com.zham.domain.usecase.PayLoanUseCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test

internal class Stores {
    val presets = mutableListOf<Preset>()
    val accounts = mutableListOf<Account>()
    val txs = mutableListOf<com.zham.domain.model.Transaction>()
    val loans = mutableListOf<Loan>()
    private var next = 1L

    val presetRepo = object : com.zham.domain.model.PresetRepository {
        override fun observePresets(): Flow<List<Preset>> = flow { emit(presets.toList()) }
        override suspend fun findById(id: Long) = presets.firstOrNull { it.id == id }
        override suspend fun save(preset: Preset): Long { presets += preset.copy(id = next); return next++ }
        override suspend fun delete(id: Long) { presets.removeAll { it.id == id } }
    }
    val accountRepo = object : com.zham.domain.model.AccountRepository {
        override fun observeAccounts(): Flow<List<Account>> = flow { emit(accounts.toList()) }
        override suspend fun save(account: Account): Long { accounts += account.copy(id = next); return next++ }
        override suspend fun delete(id: Long) { accounts.removeAll { it.id == id } }
    }
    val txRepo = object : com.zham.domain.model.TransactionRepository {
        override fun observeTransactions(): Flow<List<com.zham.domain.model.Transaction>> = flow { emit(txs.toList()) }
        override suspend fun findById(id: Long) = txs.firstOrNull { it.id == id }
        override suspend fun save(transaction: com.zham.domain.model.Transaction): Long { txs += transaction.copy(id = next); return next++ }
        override suspend fun delete(id: Long) { txs.removeAll { it.id == id } }
    }
    val moneyCategories = mutableListOf<com.zham.domain.model.MoneyCategory>()
    val moneyCategoryRepo = object : com.zham.domain.model.MoneyCategoryRepository {
        override fun observeCategories(): Flow<List<com.zham.domain.model.MoneyCategory>> = flow { emit(moneyCategories.toList()) }
        override suspend fun save(category: com.zham.domain.model.MoneyCategory): Long { moneyCategories += category.copy(id = next); return next++ }
        override suspend fun delete(id: Long) { moneyCategories.removeAll { it.id == id } }
    }
    val loanRepo = object : com.zham.domain.model.LoanRepository {
        override fun observeLoans(): Flow<List<Loan>> = flow { emit(loans.toList()) }
        override suspend fun findById(id: Long) = loans.firstOrNull { it.id == id }
        override suspend fun save(loan: Loan): Long {
            val i = loans.indexOfFirst { it.id == loan.id }
            if (i >= 0) { loans[i] = loan; return loan.id }
            loans += loan.copy(id = next); return next++
        }
        override suspend fun delete(id: Long) { loans.removeAll { it.id == id } }
    }
}

class MoneyUseCasesTest {
    private val now = Instant.parse("2026-01-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun preset(kind: PresetKind, amount: Long = 30_000, duration: Int = 0) =
        Preset(0, kind, "Bus", "Bus fare", 5, null, null, amount, duration, 0.0)

    @Test
    fun `an expense preset becomes a transaction on the first account when it names none`() = runTest {
        val s = Stores()
        s.accountRepo.save(Account(0, "Cash", "AMD", 0, "money", 0, 0.0))
        val id = s.presetRepo.save(preset(PresetKind.Expense))
        val result = ApplyPresetUseCase(s.presetRepo, s.accountRepo, s.txRepo, Mem().time, clock)(id)
        result.isRight() shouldBe true
        val tx = s.txs.single()
        tx.type shouldBe TxType.Expense
        tx.amountMinor shouldBe 30_000
        tx.title shouldBe "Bus fare"
        tx.time shouldBe now
    }

    @Test
    fun `a money preset without any account is refused`() = runTest {
        val s = Stores()
        val id = s.presetRepo.save(preset(PresetKind.Income))
        ApplyPresetUseCase(s.presetRepo, s.accountRepo, s.txRepo, Mem().time, clock)(id).isLeft() shouldBe true
    }

    @Test
    fun `an activity preset logs a record that ends now and lasts as long as set`() = runTest {
        val s = Stores()
        val mem = Mem()
        val id = s.presetRepo.save(preset(PresetKind.Activity, amount = 0, duration = 540))
        ApplyPresetUseCase(s.presetRepo, s.accountRepo, s.txRepo, mem.time, clock)(id).isRight() shouldBe true
        val entry = mem.entries.single()
        entry.end shouldBe now
        Duration.between(entry.start, entry.end) shouldBe Duration.ofHours(9)
    }

    @Test
    fun `paying a loan counts the payment and records the expense, and undo takes both back`() = runTest {
        val s = Stores()
        val account = s.accountRepo.save(Account(0, "Card", "AMD", 0, "money", 0, 0.0))
        val loanId = s.loanRepo.save(
            Loan(0, "Car", "AMD", 30_000_000, 24.0, RateKind.Nominal, 36, AmortizationType.Annuity, LocalDate.of(2026, 1, 31), 0, account, null)
        )
        val pay = PayLoanUseCase(s.loanRepo, s.txRepo, clock)
        pay.pay(loanId).isRight() shouldBe true
        s.loans.single().paidCount shouldBe 1
        s.txs.single().title shouldBe "Car 1/36"
        s.txs.single().amountMinor shouldBe 1_176_986
        pay.undo(loanId).isRight() shouldBe true
        s.loans.single().paidCount shouldBe 0
        s.txs.size shouldBe 0
    }

    @Test
    fun `a loan without an account only counts the payment`() = runTest {
        val s = Stores()
        val loanId = s.loanRepo.save(
            Loan(0, "Car", "AMD", 30_000_000, 24.0, RateKind.Nominal, 36, AmortizationType.Annuity, LocalDate.of(2026, 1, 31), 35, null, null)
        )
        val pay = PayLoanUseCase(s.loanRepo, s.txRepo, clock)
        pay.pay(loanId).isRight() shouldBe true
        pay.pay(loanId).isLeft() shouldBe true // the schedule is finished
        s.txs.size shouldBe 0
    }

    @Test
    fun `money survives a backup round trip and a second restore adds nothing`() = runTest {
        val a = Mem()
        val s = a.stores
        val cash = s.accountRepo.save(Account(0, "Cash", "AMD", 1, "i", 5_000, 0.0))
        val card = s.accountRepo.save(Account(0, "Card", "USD", 2, "i", 0, 1.0))
        val food = s.moneyCategoryRepo.save(com.zham.domain.model.MoneyCategory(0, "Food", 3, "i", 0.0))
        s.txRepo.save(com.zham.domain.model.Transaction(0, TxType.Expense, 300, cash, null, null, food, "Bus", "d", now, null))
        s.txRepo.save(com.zham.domain.model.Transaction(0, TxType.Transfer, 100, cash, card, 1, null, "", "", now.plusSeconds(60), null))
        s.presetRepo.save(Preset(0, PresetKind.Expense, "Bus", "Bus fare", food, null, cash, 300, 0, 0.0))
        s.loanRepo.save(Loan(0, "Car", "AMD", 30_000_000, 24.0, RateKind.Nominal, 36, AmortizationType.Annuity, LocalDate.of(2026, 2, 1), 3, cash, food))

        val text = com.zham.domain.usecase.FullBackup.encode(
            com.zham.domain.usecase.BuildBackupUseCase(a.time, a.category, a.project, a.plan, a.goal, a.note, a.finance())()
        )
        val decoded = com.zham.domain.usecase.FullBackup.decode(text)!!
        decoded.finance.transactions.size shouldBe 2

        val b = Mem()
        val restore = com.zham.domain.usecase.RestoreBackupUseCase(b.time, b.category, b.project, b.plan, b.goal, b.note, b.restorer())
        restore(decoded) { 0 }.money shouldBe 4 // 2 transactions, 1 preset, 1 loan; accounts and categories are not counted
        b.stores.accounts.map { it.name } shouldBe listOf("Cash", "Card")
        b.stores.txs.size shouldBe 2
        b.stores.txs.first { it.type == TxType.Transfer }.toAccountId shouldBe b.stores.accounts.first { it.name == "Card" }.id
        b.stores.loans.single().paidCount shouldBe 3
        restore(decoded) { 0 }.money shouldBe 0
        b.stores.txs.size shouldBe 2
    }
}
