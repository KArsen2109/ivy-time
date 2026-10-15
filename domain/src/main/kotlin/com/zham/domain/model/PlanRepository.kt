package com.zham.domain.model

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PlanRepository {
    fun observePlans(): Flow<List<PlanBlock>>
    fun observeCompletions(): Flow<List<PlanCompletion>>
    suspend fun findById(id: Long): PlanBlock?

    /** Inserts when [PlanBlock.id] is 0, otherwise updates. */
    suspend fun save(plan: PlanBlock)
    suspend fun delete(id: Long)

    suspend fun setCompleted(planId: Long, date: LocalDate, completed: Boolean)
}
