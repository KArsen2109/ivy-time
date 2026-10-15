package com.ivy.time.domain.usecase

import com.ivy.time.domain.model.TimeEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Total tracked time of [projectId] (null = no project). */
data class ProjectTotal(val projectId: Long?, val total: Duration)

/**
 * Time spent in `[from, to)` per project, biggest first.
 * Entries are clipped to the range, running ones count until [now].
 */
fun summarize(
    entries: List<TimeEntry>,
    from: Instant,
    to: Instant,
    now: Instant,
): List<ProjectTotal> = entries
    .mapNotNull { entry ->
        val start = maxOf(entry.start, from)
        val end = minOf(entry.end ?: now, to)
        if (end.isAfter(start)) entry.projectId to Duration.between(start, end) else null
    }
    .groupBy({ it.first }, { it.second })
    .map { (projectId, durations) ->
        ProjectTotal(projectId, durations.fold(Duration.ZERO, Duration::plus))
    }
    .sortedByDescending { it.total }

fun LocalDate.startInstant(zone: ZoneId): Instant = atStartOfDay(zone).toInstant()

fun Duration.formatHms(): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}

/** "2h 05m", "45m" or "0m". */
fun Duration.formatShort(): String {
    val minutes = toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "${minutes}m"
}
