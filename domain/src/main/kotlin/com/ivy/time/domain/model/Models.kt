package com.ivy.time.domain.model

import java.time.Duration
import java.time.Instant

/** A thing you spend time on (the "category" of Ivy Wallet). [color] is ARGB. */
data class Project(
    val id: Long,
    val name: String,
    val color: Int,
)

/** A time record (the "transaction" of Ivy Wallet). A null [end] means the timer is running. */
data class TimeEntry(
    val id: Long,
    val projectId: Long?,
    val note: String,
    val start: Instant,
    val end: Instant?,
) {
    val isRunning: Boolean get() = end == null

    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)
}

sealed interface TimeError {
    data object TimerAlreadyRunning : TimeError
    data object NoRunningTimer : TimeError
    data object EndNotAfterStart : TimeError
    data object BlankName : TimeError
}
