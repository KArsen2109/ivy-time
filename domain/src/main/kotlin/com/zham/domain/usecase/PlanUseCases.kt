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
import kotlinx.coroutines.flow.firstOrNull
import java.time.Clock
import java.time.Instant
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
        reminderMinutes: Int? = null,
        point: Boolean = false,
    ): Either<TimeError, Unit> {
        if (!point && start == end) return TimeError.NonPositiveDuration.left()
        if (repeat is RepeatRule.Weekly && repeat.days.isEmpty()) {
            return TimeError.NoWeekdaysChosen.left()
        }
        repository.save(
            PlanBlock(
                id, categoryId, projectId, note.trim(), start, if (point) start else end, startDate, repeat,
                description.trim(), reminderMinutes?.takeIf { it >= 0 }, point,
            )
        )
        return Unit.right()
    }
}

/**
 * Marks one dated occurrence as done and logs the time it took.
 *
 * Without [actualStart] and [actualEnd] the record is the block as planned, cut off at the present:
 * nothing in the future is logged. A block done ahead of time passes the real start and end, and
 * those are what is recorded. A block with no length is logged as a moment, now.
 */
class CompletePlanUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    private val timeRepository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        planId: Long,
        date: LocalDate,
        actualStart: Instant? = null,
        actualEnd: Instant? = null,
    ): Either<TimeError, Unit> {
        val plan = planRepository.findById(planId) ?: return TimeError.PlanNotFound.left()
        val occurrence = plan.occurrenceOn(date, clock.zone, emptySet())
            ?: return TimeError.PlanNotFound.left()

        // A second tap must not log the same time again.
        val alreadyDone = planRepository.observeCompletions().firstOrNull().orEmpty()
            .any { it.planId == planId && it.date == date }
        if (alreadyDone) return Unit.right()

        val now = clock.instant()
        val start: Instant
        val end: Instant
        when {
            plan.point -> {
                start = now
                end = now
            }
            actualStart != null && actualEnd != null -> {
                if (!actualEnd.isAfter(actualStart)) return TimeError.EndNotAfterStart.left()
                if (actualEnd.isAfter(now)) return TimeError.EndInTheFuture.left()
                start = actualStart
                end = actualEnd
            }
            else -> {
                start = occurrence.startsAt
                end = minOf(occurrence.endsAt, now)
                if (!end.isAfter(start)) return TimeError.EndInTheFuture.left()
            }
        }

        timeRepository.save(
            TimeEntry(
                id = 0,
                categoryId = plan.categoryId,
                projectId = plan.projectId,
                note = plan.note,
                start = start,
                end = end,
                description = plan.description,
                point = plan.point,
                planId = planId,
                planDate = date,
            )
        )
        planRepository.setCompleted(planId, date, completed = true)
        return Unit.right()
    }
}

/** Takes a block's completion back and removes the record that ticking it created. */
class UncompletePlanUseCase @Inject constructor(
    private val planRepository: PlanRepository,
    private val timeRepository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(planId: Long, date: LocalDate): Either<TimeError, Unit> {
        val plan = planRepository.findById(planId) ?: return TimeError.PlanNotFound.left()
        val occurrence = plan.occurrenceOn(date, clock.zone, emptySet())
            ?: return TimeError.PlanNotFound.left()

        // The record made by completing is recognised by what it was copied from.
        timeRepository.observeEntries().firstOrNull().orEmpty()
            .filter {
                // Records made since they were linked carry the block; older ones are matched by content.
                (it.planId == planId && it.planDate == date) ||
                    (it.planId == null && it.start == occurrence.startsAt && it.end != null &&
                        it.categoryId == plan.categoryId && it.projectId == plan.projectId &&
                        it.note == plan.note)
            }
            .forEach { timeRepository.delete(it.id) }
        planRepository.setCompleted(planId, date, completed = false)
        return Unit.right()
    }
}
