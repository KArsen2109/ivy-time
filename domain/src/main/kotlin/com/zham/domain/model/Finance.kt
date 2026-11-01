package com.zham.domain.model

import kotlinx.coroutines.flow.Flow
import java.time.Instant

enum class TxType { Expense, Income, Transfer }

/**
 * A place money is kept: cash, a card, a savings account. Amounts are whole minor units
 * (1/100), so sums never drift the way floating point does.
 */
data class Account(
    val id: Long,
    val name: String,
    /** Three-letter code such as AMD, USD or EUR. */
    val currency: String,
    val color: Int,
    val icon: String,
    val initialMinor: Long,
    val orderNum: Double,
    val archived: Boolean = false,
)

/** What money is spent on or comes from, e.g. "Transport" or "Salary". */
data class MoneyCategory(
    val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val orderNum: Double,
)

/**
 * One movement of money. A transfer leaves [accountId] and arrives in [toAccountId]; when the two
 * accounts hold different currencies [toAmountMinor] is what arrives, otherwise it equals [amountMinor].
 */
data class Transaction(
    val id: Long,
    val type: TxType,
    val amountMinor: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val toAmountMinor: Long?,
    val categoryId: Long?,
    val title: String,
    val description: String,
    val time: Instant,
    /** The time-tracking project this belongs to, if any. */
    val projectId: Long?,
)

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun save(account: Account): Long

    /** Also deletes every transaction that touches the account. */
    suspend fun delete(id: Long)
}

interface MoneyCategoryRepository {
    fun observeCategories(): Flow<List<MoneyCategory>>
    suspend fun save(category: MoneyCategory): Long

    /** Transactions keep their time and amount but lose the category. */
    suspend fun delete(id: Long)
}

interface TransactionRepository {
    fun observeTransactions(): Flow<List<Transaction>>
    suspend fun findById(id: Long): Transaction?
    suspend fun save(transaction: Transaction): Long
    suspend fun delete(id: Long)
}

/** A ready-made entry: tap it and the record is made with every field already filled in. */
enum class PresetKind { Expense, Income, Activity }

data class Preset(
    val id: Long,
    val kind: PresetKind,
    /** The label on its chip. */
    val name: String,
    val title: String,
    /** A money category for money presets, a time category for activities. */
    val categoryId: Long?,
    val projectId: Long?,
    val accountId: Long?,
    val amountMinor: Long,
    /** How long an activity lasts, in minutes. */
    val durationMinutes: Int,
    val orderNum: Double,
)

interface PresetRepository {
    fun observePresets(): Flow<List<Preset>>
    suspend fun findById(id: Long): Preset?
    suspend fun save(preset: Preset): Long
    suspend fun delete(id: Long)
}
