package com.zham.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the time was spent on, e.g. "Work" or "Sport".
 * [color] is ARGB, [icon] is a key of the app's icon set.
 */
data class Category(
    val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val orderNum: Double,
)

/** A longer running effort inside one category, e.g. "Work" and a client site. */
data class Project(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val color: Int,
    val archived: Boolean,
)

/** A time record. A null [end] means the timer is running. */
data class TimeEntry(
    val id: Long,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val start: Instant,
    val end: Instant?,
    val description: String = "",
) {
    val isRunning: Boolean get() = end == null

    fun duration(now: Instant): Duration = Duration.between(start, end ?: now)
}

enum class GoalPeriod { Week, Month }

/** Spend at least, or at most, [target] on a category. */
enum class GoalType { AtLeast, AtMost }

data class Goal(
    val id: Long,
    val name: String,
    /** Null means every category counts. */
    val categoryId: Long?,
    val target: Duration,
    val period: GoalPeriod,
    val type: GoalType,
    /** When set, only records of this project count. Wins over [matchTitle]. */
    val projectId: Long? = null,
    /** When set, only records in the category titled like the goal count. */
    val matchTitle: Boolean = false,
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
