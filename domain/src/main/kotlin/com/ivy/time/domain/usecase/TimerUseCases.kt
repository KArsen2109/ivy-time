package com.ivy.time.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeError
import com.ivy.time.domain.model.TimeRepository
import java.time.Clock
import java.time.Duration

class StartTimerUseCase(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(projectId: Long?, note: String): Either<TimeError, Unit> {
        if (repository.findRunning() != null) return TimeError.TimerAlreadyRunning.left()
        repository.save(
            TimeEntry(
                id = 0,
                projectId = projectId,
                note = note.trim(),
                start = clock.instant(),
                end = null,
            )
        )
        return Unit.right()
    }
}

class StopTimerUseCase(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Either<TimeError, Unit> {
        val running = repository.findRunning() ?: return TimeError.NoRunningTimer.left()
        repository.save(running.copy(end = clock.instant()))
        return Unit.right()
    }
}

/** Logs already spent time: an entry of [duration] that ends now. */
class LogTimeUseCase(
    private val repository: TimeRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        projectId: Long?,
        note: String,
        duration: Duration,
    ): Either<TimeError, Unit> {
        if (duration.isZero || duration.isNegative) return TimeError.EndNotAfterStart.left()
        val end = clock.instant()
        repository.save(
            TimeEntry(
                id = 0,
                projectId = projectId,
                note = note.trim(),
                start = end.minus(duration),
                end = end,
            )
        )
        return Unit.right()
    }
}

class SaveProjectUseCase(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(name: String, color: Int): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        repository.save(Project(id = 0, name = name.trim(), color = color))
        return Unit.right()
    }
}
