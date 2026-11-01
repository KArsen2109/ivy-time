package com.zham.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeError
import com.zham.domain.model.TimeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Checking for a running timer and saving the new one must not interleave, or two could start. */
private val timerLock = Mutex()

class StartTimerUseCase @Inject constructor(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        categoryId: Long?,
        projectId: Long?,
        note: String,
        description: String = "",
    ): Either<TimeError, Unit> = timerLock.withLock {
        if (repository.findRunning() != null) return@withLock TimeError.TimerAlreadyRunning.left()
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
        Unit.right()
    }
}

class StopTimerUseCase @Inject constructor(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Either<TimeError, Unit> = timerLock.withLock {
        val running = repository.findRunning() ?: return@withLock TimeError.NoRunningTimer.left()
        repository.save(running.copy(end = clock.instant()))
        Unit.right()
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
        point: Boolean = false,
    ): Either<TimeError, Unit> {
        // A moment has no length, so its end is its start; only a stretch of time needs an end after the start.
        if (!point && !end.isAfter(start)) return TimeError.EndNotAfterStart.left()
        if ((if (point) start else end).isAfter(clock.instant())) return TimeError.EndInTheFuture.left()
        // Editing a record made by ticking off a block must not cut its link to that block.
        val origin = if (id != 0L) repository.findById(id) else null
        repository.save(
            TimeEntry(
                id = id,
                categoryId = categoryId,
                projectId = projectId,
                note = note.trim(),
                start = start,
                end = if (point) start else end,
                description = description.trim(),
                point = point,
                planId = origin?.planId,
                planDate = origin?.planDate,
            )
        )
        return Unit.right()
    }
}
