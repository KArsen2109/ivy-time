package com.ivy.time.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the time was spent on, e.g. "Work", "Sport" (the Category of Ivy Wallet).
 * [color] is ARGB, [icon] is a key of the app's icon set.
 */
data class Category(
    val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val orderNum: Double,
)

/** A longer running effort inside a category, e.g. "Work → Ivy Time" (the Account of Ivy Wallet). */
data class Project(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val color: Int,
    val archived: Boolean,
)

/** A time record (the Transaction of Ivy Wallet). A null [end] means the timer is running. */
data class TimeEntry(
    val id: Long,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val start: Instant,
    val end: Instant?,
) {
    val isRunning: Boolean get() = end == null

    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)
}

enum class GoalPeriod { Week, Month }

/** Spend at least, or at most, [target] on a category (the Budget of Ivy Wallet). */
enum class GoalType { AtLeast, AtMost }

data class Goal(
    val id: Long,
    val name: String,
    /** Null means every category counts. */
    val categoryId: Long?,
    val target: Duration,
    val period: GoalPeriod,
    val type: GoalType,
)

sealed interface TimeError {
    data object TimerAlreadyRunning : TimeError
    data object NoRunningTimer : TimeError
    data object EndNotAfterStart : TimeError
    data object EndInTheFuture : TimeError
    data object BlankName : TimeError
    data object NonPositiveDuration : TimeError
    data object NoWeekdaysChosen : TimeError
    data object PlanNotFound : TimeError
}
