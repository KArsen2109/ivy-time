package com.zham.domain.usecase

import com.zham.domain.model.Account
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Loan
import com.zham.domain.model.LoanRepository
import com.zham.domain.model.MoneyCategory
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.Preset
import com.zham.domain.model.PresetKind
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.RateKind
import com.zham.domain.model.Transaction
import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.TxType
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** The money side of a backup. Links between things are written as names, like the rest of the backup. */
data class FinanceData(
    val accounts: List<AccountItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val presets: List<PresetItem> = emptyList(),
    val loans: List<LoanItem> = emptyList(),
) {
    data class AccountItem(val name: String, val currency: String, val color: Int, val icon: String, val initialMinor: Long, val archived: Boolean)
    data class CategoryItem(val name: String, val color: Int, val icon: String)
    data class TransactionItem(
        val type: TxType, val amountMinor: Long, val account: String, val toAccount: String, val toAmountMinor: Long?,
        val category: String, val title: String, val description: String, val time: Instant, val project: String,
    )
    /** [category] names a money category, or a time category for an activity. */
    data class PresetItem(
        val kind: PresetKind, val name: String, val title: String, val category: String, val project: String,
        val account: String, val amountMinor: Long, val durationMinutes: Int,
    )
    data class LoanItem(
        val name: String, val currency: String, val principalMinor: Long, val ratePercent: Double, val rateKind: RateKind,
        val termMonths: Int, val type: AmortizationType, val firstPayment: LocalDate, val paidCount: Int,
        val account: String, val category: String,
    )

    val isEmpty: Boolean
        get() = accounts.isEmpty() && categories.isEmpty() && transactions.isEmpty() && presets.isEmpty() && loans.isEmpty()
}

internal object FinanceCodec {
    fun encode(f: FinanceData): Map<String, Any?> = linkedMapOf(
        "accounts" to f.accounts.map {
            mapOf("name" to it.name, "currency" to it.currency, "color" to it.color, "icon" to it.icon,
                "initialMinor" to it.initialMinor, "archived" to it.archived)
        },
        "moneyCategories" to f.categories.map { mapOf("name" to it.name, "color" to it.color, "icon" to it.icon) },
        "transactions" to f.transactions.map {
            mapOf("type" to it.type.name, "amountMinor" to it.amountMinor, "account" to it.account,
                "toAccount" to it.toAccount, "toAmountMinor" to it.toAmountMinor, "category" to it.category,
                "title" to it.title, "description" to it.description, "time" to it.time.toString(), "project" to it.project)
        },
        "presets" to f.presets.map {
            mapOf("kind" to it.kind.name, "name" to it.name, "title" to it.title, "category" to it.category,
                "project" to it.project, "account" to it.account, "amountMinor" to it.amountMinor,
                "durationMinutes" to it.durationMinutes)
        },
        "loans" to f.loans.map {
            mapOf("name" to it.name, "currency" to it.currency, "principalMinor" to it.principalMinor,
                "ratePercent" to it.ratePercent, "rateKind" to it.rateKind.name, "termMonths" to it.termMonths,
                "type" to it.type.name, "firstPayment" to it.firstPayment.toString(), "paidCount" to it.paidCount,
                "account" to it.account, "category" to it.category)
        },
    )

    private fun items(root: Map<*, *>, key: String) = (root[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    private fun Map<*, *>.str(key: String) = this[key] as? String ?: ""
    private fun Map<*, *>.long(key: String) = (this[key] as? Number)?.toLong()
    private fun Map<*, *>.int(key: String) = (this[key] as? Number)?.toInt()

    fun decode(root: Map<*, *>): FinanceData = FinanceData(
        accounts = items(root, "accounts").mapNotNull {
            FinanceData.AccountItem(
                it.str("name").ifBlank { return@mapNotNull null }, it.str("currency").ifBlank { "USD" },
                it.int("color") ?: return@mapNotNull null, it.str("icon"), it.long("initialMinor") ?: 0L, it["archived"] == true,
            )
        },
        categories = items(root, "moneyCategories").mapNotNull {
            FinanceData.CategoryItem(it.str("name").ifBlank { return@mapNotNull null }, it.int("color") ?: return@mapNotNull null, it.str("icon"))
        },
        transactions = items(root, "transactions").mapNotNull {
            FinanceData.TransactionItem(
                TxType.entries.firstOrNull { t -> t.name == it.str("type") } ?: return@mapNotNull null,
                it.long("amountMinor") ?: return@mapNotNull null, it.str("account"), it.str("toAccount"), it.long("toAmountMinor"),
                it.str("category"), it.str("title"), it.str("description"),
                runCatching { Instant.parse(it.str("time")) }.getOrNull() ?: return@mapNotNull null, it.str("project"),
            )
        },
        presets = items(root, "presets").mapNotNull {
            FinanceData.PresetItem(
                PresetKind.entries.firstOrNull { k -> k.name == it.str("kind") } ?: return@mapNotNull null,
                it.str("name").ifBlank { return@mapNotNull null }, it.str("title"), it.str("category"), it.str("project"),
                it.str("account"), it.long("amountMinor") ?: 0L, it.int("durationMinutes") ?: 0,
            )
        },
        loans = items(root, "loans").mapNotNull {
            FinanceData.LoanItem(
                it.str("name").ifBlank { return@mapNotNull null }, it.str("currency").ifBlank { "USD" },
                it.long("principalMinor") ?: return@mapNotNull null,
                (it["ratePercent"] as? Number)?.toDouble() ?: return@mapNotNull null,
                RateKind.entries.firstOrNull { k -> k.name == it.str("rateKind") } ?: RateKind.Nominal,
                it.int("termMonths") ?: return@mapNotNull null,
                AmortizationType.entries.firstOrNull { k -> k.name == it.str("type") } ?: AmortizationType.Annuity,
                runCatching { LocalDate.parse(it.str("firstPayment")) }.getOrNull() ?: return@mapNotNull null,
                it.int("paidCount") ?: 0, it.str("account"), it.str("category"),
            )
        },
    )
}

class FinanceBackupBuilder @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: MoneyCategoryRepository,
    private val transactions: TransactionRepository,
    private val presets: PresetRepository,
    private val loans: LoanRepository,
) {
    /** [timeCategories] and [projects] name the time categories and projects that presets and transactions point at. */
    suspend fun build(timeCategories: Map<Long, String>, projects: Map<Long, String>): FinanceData {
        val accountList = accounts.observeAccounts().firstOrNull().orEmpty()
        val categoryList = categories.observeCategories().firstOrNull().orEmpty()
        val an = accountList.associate { it.id to it.name }
        val cn = categoryList.associate { it.id to it.name }
        return FinanceData(
            accounts = accountList.map { FinanceData.AccountItem(it.name, it.currency, it.color, it.icon, it.initialMinor, it.archived) },
            categories = categoryList.map { FinanceData.CategoryItem(it.name, it.color, it.icon) },
            transactions = transactions.observeTransactions().firstOrNull().orEmpty().map {
                FinanceData.TransactionItem(
                    it.type, it.amountMinor, an[it.accountId].orEmpty(), an[it.toAccountId].orEmpty(), it.toAmountMinor,
                    cn[it.categoryId].orEmpty(), it.title, it.description, it.time, projects[it.projectId].orEmpty(),
                )
            },
            presets = presets.observePresets().firstOrNull().orEmpty().map {
                FinanceData.PresetItem(
                    it.kind, it.name, it.title,
                    (if (it.kind == PresetKind.Activity) timeCategories[it.categoryId] else cn[it.categoryId]).orEmpty(),
                    projects[it.projectId].orEmpty(), an[it.accountId].orEmpty(), it.amountMinor, it.durationMinutes,
                )
            },
            loans = loans.observeLoans().firstOrNull().orEmpty().map {
                FinanceData.LoanItem(
                    it.name, it.currency, it.principalMinor, it.annualRatePercent, it.rateKind, it.termMonths, it.type,
                    it.firstPaymentDate, it.paidCount, an[it.accountId].orEmpty(), cn[it.categoryId].orEmpty(),
                )
            },
        )
    }
}

class FinanceRestorer @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: MoneyCategoryRepository,
    private val transactions: TransactionRepository,
    private val presets: PresetRepository,
    private val loans: LoanRepository,
) {
    /**
     * Adds what is missing, matching by name or by content, so a second restore adds nothing.
     * [timeCategoryId] and [projectId] resolve the names of time categories and projects, which exist by now.
     */
    suspend fun restore(
        data: FinanceData,
        timeCategoryId: (String) -> Long?,
        projectId: (String) -> Long?,
    ): Int {
        if (data.isEmpty) return 0
        var added = 0

        var accountList = accounts.observeAccounts().firstOrNull().orEmpty()
        data.accounts.forEach { item ->
            if (accountList.none { it.name.equals(item.name, true) && it.currency.equals(item.currency, true) }) {
                accounts.save(Account(0, item.name, item.currency, item.color, item.icon, item.initialMinor, accountList.size.toDouble(), item.archived))
                accountList = accounts.observeAccounts().firstOrNull().orEmpty()
            }
        }
        var categoryList = categories.observeCategories().firstOrNull().orEmpty()
        data.categories.forEach { item ->
            if (categoryList.none { it.name.equals(item.name, true) }) {
                categories.save(MoneyCategory(0, item.name, item.color, item.icon, categoryList.size.toDouble()))
                categoryList = categories.observeCategories().firstOrNull().orEmpty()
            }
        }
        fun account(name: String) = accountList.firstOrNull { it.name.equals(name, true) }?.id
        fun category(name: String) = categoryList.firstOrNull { it.name.equals(name, true) }?.id

        val known = transactions.observeTransactions().firstOrNull().orEmpty()
            .map { listOf(it.time, it.type, it.amountMinor, it.accountId, it.title) }.toMutableSet()
        data.transactions.forEach { item ->
            val from = account(item.account) ?: return@forEach
            if (!known.add(listOf(item.time, item.type, item.amountMinor, from, item.title))) return@forEach
            transactions.save(
                Transaction(
                    0, item.type, item.amountMinor, from, account(item.toAccount), item.toAmountMinor,
                    category(item.category), item.title, item.description, item.time, projectId(item.project),
                )
            )
            added++
        }
        val knownPresets = presets.observePresets().firstOrNull().orEmpty().map { it.kind to it.name.lowercase() }.toMutableSet()
        data.presets.forEach { item ->
            if (!knownPresets.add(item.kind to item.name.lowercase())) return@forEach
            presets.save(
                Preset(
                    0, item.kind, item.name, item.title,
                    if (item.kind == PresetKind.Activity) timeCategoryId(item.category) else category(item.category),
                    projectId(item.project), account(item.account), item.amountMinor, item.durationMinutes,
                    System.currentTimeMillis().toDouble(),
                )
            )
            added++
        }
        val knownLoans = loans.observeLoans().firstOrNull().orEmpty().map { it.name.lowercase() to it.principalMinor }.toMutableSet()
        data.loans.forEach { item ->
            if (!knownLoans.add(item.name.lowercase() to item.principalMinor)) return@forEach
            loans.save(
                Loan(
                    0, item.name, item.currency, item.principalMinor, item.ratePercent, item.rateKind, item.termMonths,
                    item.type, item.firstPayment, item.paidCount, account(item.account), category(item.category),
                )
            )
            added++
        }
        return added
    }
}
