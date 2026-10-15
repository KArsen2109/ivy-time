package com.zham.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** How a block repeats. [Weekly] with an empty day set never recurs. */
sealed interface RepeatRule {
    data object Once : RepeatRule
    data object Daily : RepeatRule
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule
    data object Monthly : RepeatRule
}

/**
 * A block of time set aside on the calendar.
 * [end] not after [start] means the block runs past midnight.
 */
data class PlanBlock(
    val id: Long,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val start: LocalTime,
    val end: LocalTime,
    val startDate: LocalDate,
    val repeat: RepeatRule,
    val description: String = "",
) {
    val duration: Duration
        get() = Duration.between(start, end).let { if (it.isNegative || it.isZero) it.plusDays(1) else it }
}

/** Marks one dated occurrence of a block as dealt with. */
data class PlanCompletion(val planId: Long, val date: LocalDate)

/** A block placed on a specific day, ready to be drawn or compared against reality. */
data class PlanOccurrence(
    val plan: PlanBlock,
    val date: LocalDate,
    val startsAt: Instant,
    val endsAt: Instant,
    val done: Boolean,
)

fun PlanBlock.occursOn(date: LocalDate): Boolean {
    if (date.isBefore(startDate)) return false
    return when (val rule = repeat) {
        RepeatRule.Once -> date == startDate
        RepeatRule.Daily -> true
        is RepeatRule.Weekly -> date.dayOfWeek in rule.days
        RepeatRule.Monthly -> date.dayOfMonth == startDate.dayOfMonth
    }
}

fun PlanBlock.occurrenceOn(
    date: LocalDate,
    zone: ZoneId,
    completed: Set<PlanCompletion>,
): PlanOccurrence? {
    if (!occursOn(date)) return null
    val startsAt = date.atTime(start).atZone(zone).toInstant()
    return PlanOccurrence(
        plan = this,
        date = date,
        startsAt = startsAt,
        endsAt = startsAt.plus(duration),
        done = PlanCompletion(id, date) in completed,
    )
}

fun occurrencesOn(
    plans: List<PlanBlock>,
    date: LocalDate,
    zone: ZoneId,
    completed: Set<PlanCompletion>,
): List<PlanOccurrence> = plans
    .mapNotNull { it.occurrenceOn(date, zone, completed) }
    .sortedBy { it.startsAt }
