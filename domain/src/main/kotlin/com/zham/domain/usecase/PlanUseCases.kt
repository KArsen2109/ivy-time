package com.zham.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeError
import com.zham.domain.model.TimeRepository
import com.zham.domain.model.occurrenceOn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class SavePlanUseCase @Inject constructor(
    private val repository: PlanRepository,
) {
    suspend operator fun invoke(
        id: Long,
        categoryId: Long?,
        projectId: Long?,
        note: String,
        start: LocalTime,
        end: LocalTime,
        startDate: LocalDate,
        repeat: RepeatRule,
        description: String = "",
    ): Either<TimeError, Unit> {
        if (start == end) return TimeError.NonPositiveDuration.left()
        if (repeat is RepeatRule.Weekly && repeat.days.isEmpty()) {
            return TimeError.NoWeekdaysChosen.left()
        }
        repository.save(
            PlanBlock(
                id, categoryId, projectId, note.trim(), start, end, startDate, repeat,
                description.trim(),
            )
        )
        return Unit.right()
    }
}

/**
 * Marks one dated occurrence as done and logs the time it reserved.
 * Nothing in the future is logged, so ticking a block early records only what has passed.
 */
class CompletePlanUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    private val timeRepository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(planId: Long, date: LocalDate): Either<TimeError, Unit> {
        val plan = planRepository.findById(planId) ?: return TimeError.PlanNotFound.left()
        val occurrence = plan.occurrenceOn(date, clock.zone, emptySet())
            ?: return TimeError.PlanNotFound.left()

        val end = minOf(occurrence.endsAt, clock.instant())
        if (!end.isAfter(occurrence.startsAt)) return TimeError.EndInTheFuture.left()

        timeRepository.save(
            TimeEntry(
                id = 0,
                categoryId = plan.categoryId,
                projectId = plan.projectId,
                note = plan.note,
                start = occurrence.startsAt,
                end = end,
                description = plan.description,
            )
        )
        planRepository.setCompleted(planId, date, completed = true)
        return Unit.right()
    }
}
