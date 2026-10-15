package com.zham.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeError
import com.zham.domain.model.TimeRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class StartTimerUseCase @Inject constructor(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        categoryId: Long?,
        projectId: Long?,
        note: String,
        description: String = "",
    ): Either<TimeError, Unit> {
        if (repository.findRunning() != null) return TimeError.TimerAlreadyRunning.left()
        repository.save(
            TimeEntry(
                id = 0,
                categoryId = categoryId,
                projectId = projectId,
                note = note.trim(),
                start = clock.instant(),
                end = null,
                description = description.trim(),
            )
        )
        return Unit.right()
    }
}

class StopTimerUseCase @Inject constructor(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Either<TimeError, Unit> {
        val running = repository.findRunning() ?: return TimeError.NoRunningTimer.left()
        repository.save(running.copy(end = clock.instant()))
        return Unit.right()
    }
}

/**
 * Creates or updates a hand-written entry. [id] 0 inserts.
 * The end must be after the start and must not lie in the future.
 */
class SaveEntryUseCase @Inject constructor(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        id: Long,
        categoryId: Long?,
        projectId: Long?,
        note: String,
        start: Instant,
        end: Instant,
        description: String = "",
    ): Either<TimeError, Unit> {
        if (!end.isAfter(start)) return TimeError.EndNotAfterStart.left()
        if (end.isAfter(clock.instant())) return TimeError.EndInTheFuture.left()
        repository.save(
            TimeEntry(
                id = id,
                categoryId = categoryId,
                projectId = projectId,
                note = note.trim(),
                start = start,
                end = end,
                description = description.trim(),
            )
        )
        return Unit.right()
    }
}
