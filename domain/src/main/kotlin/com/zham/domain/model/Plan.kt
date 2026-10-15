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
    /** Minutes before the start to remind the user; null means no reminder, 0 means at the start. */
    val reminderMinutes: Int? = null,
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
        // A block set for the 31st falls on the last day of shorter months instead of skipping them.
        RepeatRule.Monthly -> date.dayOfMonth == minOf(startDate.dayOfMonth, date.lengthOfMonth())
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

/**
 * What is on [date]: the blocks that start that day, plus the tail of yesterday's blocks that
 * ran past midnight. A spilled occurrence keeps yesterday's [PlanOccurrence.date].
 */
fun occurrencesOn(
    plans: List<PlanBlock>,
    date: LocalDate,
    zone: ZoneId,
    completed: Set<PlanCompletion>,
): List<PlanOccurrence> {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val spilled = plans
        .mapNotNull { it.occurrenceOn(date.minusDays(1), zone, completed) }
        .filter { it.endsAt.isAfter(dayStart) }
    return (spilled + plans.mapNotNull { it.occurrenceOn(date, zone, completed) })
        .sortedBy { it.startsAt }
}
