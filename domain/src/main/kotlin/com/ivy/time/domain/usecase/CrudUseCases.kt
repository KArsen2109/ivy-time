package com.ivy.time.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalRepository
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.PlannedEntry
import com.ivy.time.domain.model.PlannedRepository
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.Recurrence
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeError
import com.ivy.time.domain.model.TimeRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class SaveCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        color: Int,
        icon: String,
        orderNum: Double,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        repository.save(Category(id, name.trim(), color, icon, orderNum))
        return Unit.right()
    }
}

class SaveProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        categoryId: Long?,
        color: Int,
        archived: Boolean = false,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        repository.save(Project(id, name.trim(), categoryId, color, archived))
        return Unit.right()
    }
}

class SavePlannedUseCase @Inject constructor(
    private val repository: PlannedRepository,
) {
    suspend operator fun invoke(
        id: Long,
        categoryId: Long?,
        projectId: Long?,
        note: String,
        duration: Duration,
        date: LocalDate,
        time: LocalTime,
        recurrence: Recurrence,
    ): Either<TimeError, Unit> {
        if (duration.isZero || duration.isNegative) return TimeError.NonPositiveDuration.left()
        repository.save(
            PlannedEntry(id, categoryId, projectId, note.trim(), duration, date, time, recurrence)
        )
        return Unit.right()
    }
}

/**
 * Turns a planned entry into a real one, the way "pay" does in Ivy Wallet.
 * A recurring plan moves to its next date, a one-time plan is removed.
 */
class CompletePlannedUseCase @Inject constructor(
    private val plannedRepository: PlannedRepository,
    private val timeRepository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(plannedId: Long): Either<TimeError, Unit> {
        val planned = plannedRepository.findById(plannedId)
            ?: return TimeError.NonPositiveDuration.left()
        val zone = clock.zone
        val plannedStart = planned.date.atTime(planned.time).atZone(zone).toInstant()
        // Never log time that has not passed yet.
        val end = minOf(plannedStart.plus(planned.duration), clock.instant())
        val start = end.minus(planned.duration)

        timeRepository.save(
            TimeEntry(
                id = 0,
                categoryId = planned.categoryId,
                projectId = planned.projectId,
                note = planned.note,
                start = start,
                end = end,
            )
        )
        when (planned.recurrence) {
            Recurrence.OneTime -> plannedRepository.delete(planned.id)
            else -> plannedRepository.save(planned.copy(date = planned.recurrence.next(planned.date)))
        }
        return Unit.right()
    }
}

fun Recurrence.next(date: LocalDate): LocalDate = when (this) {
    Recurrence.OneTime -> date
    Recurrence.Daily -> date.plusDays(1)
    Recurrence.Weekly -> date.plusWeeks(1)
    Recurrence.Monthly -> date.plusMonths(1)
}

class SaveGoalUseCase @Inject constructor(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        categoryId: Long?,
        target: Duration,
        period: GoalPeriod,
        type: GoalType,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        if (target.isZero || target.isNegative) return TimeError.NonPositiveDuration.left()
        repository.save(Goal(id, name.trim(), categoryId, target, period, type))
        return Unit.right()
    }
}
