package com.zham.domain.model

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class AmortizationType { Annuity, Differential }

/** Whether the rate is the bank's stated yearly rate (split into twelve) or the effective yearly rate. */
enum class RateKind { Nominal, Effective }

/**
 * A loan being repaid. [principalMinor] is what is still owed on the day tracking starts, and
 * [termMonths] how many monthly payments are left from then. [paidCount] payments have been made.
 */
data class Loan(
    val id: Long,
    val name: String,
    val currency: String,
    val principalMinor: Long,
    val annualRatePercent: Double,
    val rateKind: RateKind,
    val termMonths: Int,
    val type: AmortizationType,
    val firstPaymentDate: LocalDate,
    val paidCount: Int,
    /** The account payments are taken from, if payments should be recorded as expenses. */
    val accountId: Long?,
    val categoryId: Long?,
)

interface LoanRepository {
    fun observeLoans(): Flow<List<Loan>>
    suspend fun findById(id: Long): Loan?
    suspend fun save(loan: Loan): Long
    suspend fun delete(id: Long)
}
