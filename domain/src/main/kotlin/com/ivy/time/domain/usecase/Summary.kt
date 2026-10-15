package com.ivy.time.domain.usecase

import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.TimeEntry
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The range a screen reports on, the way Ivy Wallet's period selector works. */
enum class PeriodType { Day, Week, Month, Year }

data class DateRange(val from: LocalDate, val toExclusive: LocalDate) {
    fun startInstant(zone: ZoneId): Instant = from.atStartOfDay(zone).toInstant()
    fun endInstant(zone: ZoneId): Instant = toExclusive.atStartOfDay(zone).toInstant()
}

fun periodRange(
    type: PeriodType,
    anchor: LocalDate,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): DateRange = when (type) {
    PeriodType.Day -> DateRange(anchor, anchor.plusDays(1))
    PeriodType.Week -> anchor.with(TemporalAdjusters.previousOrSame(weekStart))
        .let { DateRange(it, it.plusWeeks(1)) }
    PeriodType.Month -> anchor.withDayOfMonth(1).let { DateRange(it, it.plusMonths(1)) }
    PeriodType.Year -> anchor.withDayOfYear(1).let { DateRange(it, it.plusYears(1)) }
}

fun DateRange.shift(type: PeriodType, steps: Long): DateRange = when (type) {
    PeriodType.Day -> DateRange(from.plusDays(steps), toExclusive.plusDays(steps))
    PeriodType.Week -> DateRange(from.plusWeeks(steps), toExclusive.plusWeeks(steps))
    PeriodType.Month -> DateRange(from.plusMonths(steps), toExclusive.plusMonths(steps))
    PeriodType.Year -> DateRange(from.plusYears(steps), toExclusive.plusYears(steps))
}

/** Total tracked time of [categoryId] (null = no category). */
data class CategoryTotal(val categoryId: Long?, val total: Duration)

/**
 * Time spent in `[from, to)` per category, biggest first.
 * Entries are clipped to the range, running ones count until [now].
 */
fun summarize(
    entries: List<TimeEntry>,
    from: Instant,
    to: Instant,
    now: Instant,
): List<CategoryTotal> = clip(entries, from, to, now)
    .groupBy({ it.first.categoryId }, { it.second })
    .map { (categoryId, durations) ->
        CategoryTotal(categoryId, durations.fold(Duration.ZERO, Duration::plus))
    }
    .sortedByDescending { it.total }

/** The same split by project instead of category. */
fun summarizeByProject(
    entries: List<TimeEntry>,
    from: Instant,
    to: Instant,
    now: Instant,
): List<Pair<Long?, Duration>> = clip(entries, from, to, now)
    .groupBy({ it.first.projectId }, { it.second })
    .map { (projectId, durations) -> projectId to durations.fold(Duration.ZERO, Duration::plus) }
    .sortedByDescending { it.second }

fun totalIn(entries: List<TimeEntry>, from: Instant, to: Instant, now: Instant): Duration =
    clip(entries, from, to, now).fold(Duration.ZERO) { acc, it -> acc + it.second }

/** Time per day of the range, for the bar chart. Days with nothing tracked are kept as zero. */
fun dailyTotals(
    entries: List<TimeEntry>,
    range: DateRange,
    zone: ZoneId,
    now: Instant,
): List<Pair<LocalDate, Duration>> {
    val days = generateSequence(range.from) { it.plusDays(1) }
        .takeWhile { it.isBefore(range.toExclusive) }
        .toList()
    return days.map { day ->
        val from = day.atStartOfDay(zone).toInstant()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant()
        day to totalIn(entries, from, to, now)
    }
}

private fun clip(
    entries: List<TimeEntry>,
    from: Instant,
    to: Instant,
    now: Instant,
): List<Pair<TimeEntry, Duration>> = entries.mapNotNull { entry ->
    val start = maxOf(entry.start, from)
    val end = minOf(entry.end ?: now, to)
    if (end.isAfter(start)) entry to Duration.between(start, end) else null
}

data class GoalProgress(
    val goal: Goal,
    val spent: Duration,
    /** 0f..1f of the target; can exceed 1 before clamping in the UI. */
    val fraction: Float,
    val met: Boolean,
)

fun goalProgress(
    goal: Goal,
    entries: List<TimeEntry>,
    today: LocalDate,
    zone: ZoneId,
    now: Instant,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): GoalProgress {
    val range = periodRange(
        type = if (goal.period == GoalPeriod.Week) PeriodType.Week else PeriodType.Month,
        anchor = today,
        weekStart = weekStart,
    )
    val relevant = entries.filter { goal.categoryId == null || it.categoryId == goal.categoryId }
    val spent = totalIn(relevant, range.startInstant(zone), range.endInstant(zone), now)
    val fraction = spent.toMillis().toFloat() / goal.target.toMillis().coerceAtLeast(1)
    return GoalProgress(
        goal = goal,
        spent = spent,
        fraction = fraction,
        met = when (goal.type) {
            GoalType.AtLeast -> spent >= goal.target
            GoalType.AtMost -> spent <= goal.target
        },
    )
}

fun Duration.formatHms(): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}

/** "2h 05m", "45m" or "0m". */
fun Duration.formatShort(): String {
    val minutes = toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m"
}

/** One record that counts toward a goal, with the part of it that falls inside the goal's period. */
data class GoalRecord(val entry: TimeEntry, val date: LocalDate, val counted: Duration)

/**
 * The records a goal's total is made of, newest first, so the number on the card can be explained.
 * The period is the week or month that contains [anchor].
 */
fun goalRecords(
    goal: Goal,
    entries: List<TimeEntry>,
    anchor: LocalDate,
    zone: ZoneId,
    now: Instant,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): List<GoalRecord> {
    val range = periodRange(
        type = if (goal.period == GoalPeriod.Week) PeriodType.Week else PeriodType.Month,
        anchor = anchor,
        weekStart = weekStart,
    )
    val from = range.startInstant(zone)
    val to = range.endInstant(zone)
    return entries
        .filter { goal.categoryId == null || it.categoryId == goal.categoryId }
        .mapNotNull { entry ->
            val start = maxOf(entry.start, from)
            val end = minOf(entry.end ?: now, to)
            if (!end.isAfter(start)) return@mapNotNull null
            GoalRecord(entry, start.atZone(zone).toLocalDate(), Duration.between(start, end))
        }
        .sortedByDescending { it.entry.start }
}
