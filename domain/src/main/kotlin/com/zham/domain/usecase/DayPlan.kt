package com.zham.domain.usecase

import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanOccurrence
import com.zham.domain.model.TimeEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day with its plan, what actually happened, and how far the two agree. */
data class DayPlan(
    val date: LocalDate,
    val occurrences: List<PlanOccurrence>,
    val entries: List<TimeEntry>,
    val planned: Duration,
    val tracked: Duration,
    /** Planned time that was actually spent on the planned category. */
    val kept: Duration,
    /** Tracked time that no plan asked for. */
    val unplanned: Duration,
) {
    /** 0f when nothing was planned. */
    val adherence: Float
        get() = if (planned.isZero) 0f else kept.toMillis().toFloat() / planned.toMillis()
}

fun dayPlan(
    date: LocalDate,
    occurrences: List<PlanOccurrence>,
    allEntries: List<TimeEntry>,
    zone: ZoneId,
    now: Instant,
): DayPlan {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val entries = allEntries.filter { it.start < dayEnd && (it.end ?: now) > dayStart }

    // Only the part of a block inside this day counts, so a block past midnight is split between days.
    val planned = occurrences.fold(Duration.ZERO) { acc, it ->
        acc + Duration.between(maxOf(it.startsAt, dayStart), minOf(it.endsAt, dayEnd))
    }
    val tracked = totalIn(entries, dayStart, dayEnd, now)

    val kept = occurrences.fold(Duration.ZERO) { acc, occurrence ->
        val matching = entries.filter {
            occurrence.plan.categoryId == null || it.categoryId == occurrence.plan.categoryId
        }
        acc + overlap(matching, maxOf(occurrence.startsAt, dayStart), minOf(occurrence.endsAt, dayEnd), now)
    }

    val plannedWindows = occurrences.map { it.startsAt to it.endsAt }
    // Entries may overlap each other; the stretches are merged so no minute is counted twice.
    val trackedRanges = merge(entries.mapNotNull { entry ->
        val start = maxOf(entry.start, dayStart)
        val end = minOf(entry.end ?: now, dayEnd)
        if (end.isAfter(start)) start to end else null
    })
    val unplanned = trackedRanges.fold(Duration.ZERO) { acc, (start, end) ->
        acc + Duration.between(start, end) - coveredBy(plannedWindows, start, end)
    }

    return DayPlan(
        date = date,
        occurrences = occurrences,
        entries = entries,
        planned = planned,
        tracked = tracked,
        kept = minOf(kept, planned),
        unplanned = maxOf(unplanned, Duration.ZERO),
    )
}

/** How much of `[from, to)` the entries cover, counting overlapping entries once. */
private fun overlap(
    entries: List<TimeEntry>,
    from: Instant,
    to: Instant,
    now: Instant,
): Duration = unionLength(entries.mapNotNull { entry ->
    val start = maxOf(entry.start, from)
    val end = minOf(entry.end ?: now, to)
    if (end.isAfter(start)) start to end else null
})

/** How much of `[from, to)` falls inside any of the windows. */
private fun coveredBy(
    windows: List<Pair<Instant, Instant>>,
    from: Instant,
    to: Instant,
): Duration = unionLength(windows.mapNotNull { (start, end) ->
    val clippedStart = maxOf(start, from)
    val clippedEnd = minOf(end, to)
    if (clippedEnd.isAfter(clippedStart)) clippedStart to clippedEnd else null
})

/** Minutes from midnight, for placing something on the hour ruler. */
fun minutesFromMidnight(instant: Instant, date: LocalDate, zone: ZoneId): Int {
    val dayStart = date.atStartOfDay(zone).toInstant()
    return (Duration.between(dayStart, instant).toMinutes()).toInt()
}

/** The completions of [planId] as a set, for a quick `in` check. */
fun List<PlanCompletion>.toSet(planId: Long): Set<LocalDate> =
    filter { it.planId == planId }.map { it.date }.toSet()
