package com.zham.domain.usecase

import com.zham.domain.model.Account
import com.zham.domain.model.Transaction
import com.zham.domain.model.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What an account holds now: where it started, plus everything that came in, minus everything that went out. */
fun accountBalance(account: Account, transactions: List<Transaction>): Long {
    var balance = account.initialMinor
    transactions.forEach { tx ->
        when (tx.type) {
            TxType.Income -> if (tx.accountId == account.id) balance += tx.amountMinor
            TxType.Expense -> if (tx.accountId == account.id) balance -= tx.amountMinor
            TxType.Transfer -> {
                if (tx.accountId == account.id) balance -= tx.amountMinor
                if (tx.toAccountId == account.id) balance += tx.toAmountMinor ?: tx.amountMinor
            }
        }
    }
    return balance
}

/** The total of every account in one currency, since money in different currencies must never be added. */
fun balancesByCurrency(accounts: List<Account>, transactions: List<Transaction>): Map<String, Long> =
    accounts.filter { !it.archived }
        .groupBy { it.currency }
        .mapValues { (_, list) -> list.sumOf { accountBalance(it, transactions) } }

data class MonthSummary(val income: Map<String, Long>, val expense: Map<String, Long>)

/** Income and spending per currency for the transactions on days inside `[from, to]`. Transfers are neither. */
fun periodSummary(
    transactions: List<Transaction>,
    accounts: List<Account>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId,
): MonthSummary {
    val currency = accounts.associate { it.id to it.currency }
    val income = linkedMapOf<String, Long>()
    val expense = linkedMapOf<String, Long>()
    transactions.forEach { tx ->
        val day = tx.time.atZone(zone).toLocalDate()
        if (day.isBefore(from) || day.isAfter(to)) return@forEach
        val code = currency[tx.accountId] ?: return@forEach
        when (tx.type) {
            TxType.Income -> income.merge(code, tx.amountMinor, Long::plus)
            TxType.Expense -> expense.merge(code, tx.amountMinor, Long::plus)
            TxType.Transfer -> Unit
        }
    }
    return MonthSummary(income, expense)
}

/** Reads what a person typed as an amount ("1 250,5", "300", "12.34") into minor units, or null if it is not one. */
fun parseAmount(text: String): Long? {
    val cleaned = text.trim().replace(" ", "").replace(" ", "").replace(',', '.')
    if (cleaned.isEmpty()) return null
    val parts = cleaned.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }
    val fraction = parts.getOrNull(1).orEmpty()
    if (!whole.all(Char::isDigit) || !fraction.all(Char::isDigit) || fraction.length > 2) return null
    if (whole.length > 13) return null
    return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
}

/** "1 250", "1 250.50" or "-300": a space between thousands, the cents only when there are any. */
fun formatMinor(minor: Long, currency: String = ""): String {
    val negative = minor < 0
    val abs = kotlin.math.abs(minor)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val cents = abs % 100
    val number = if (cents == 0L) whole else "$whole.${cents.toString().padStart(2, '0')}"
    return (if (negative) "-" else "") + number + if (currency.isEmpty()) "" else " $currency"
}

