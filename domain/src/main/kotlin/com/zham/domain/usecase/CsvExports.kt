package com.zham.domain.usecase

import com.zham.domain.model.Category
import com.zham.domain.model.Note
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.Project
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a CSV export holds. */
enum class ExportKind { Records, PlannedBlocks, Notes, ProjectsAndCategories }

/** The days an export covers, both ends included; null on a side leaves that side open. */
data class ExportPeriod(val from: LocalDate? = null, val to: LocalDate? = null) {
    fun contains(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
}

/**
 * CSV for spreadsheets. Dates and times are written in the user's time zone, in a form Excel and
 * Google Sheets read directly. The first six columns of the records file are the ones the app's
 * own import reads, so an export can be brought back.
 */
object CsvExports {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    private fun row(vararg cells: String) = cells.joinToString(",") { Csv.escape(it) }

    /**
     * Cells that begin with a character a spreadsheet reads as a formula are written with a leading
     * apostrophe, so text typed in a note can never run as one.
     */
    private fun safe(text: String): String =
        if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text

    fun records(
        entries: List<TimeEntry>,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
        period: ExportPeriod,
        zone: ZoneId,
        now: Instant,
    ): String = buildString {
        appendLine(Csv.HEADER + ",date,from,to,minutes,moment")
        entries.filter { period.contains(it.start.atZone(zone).toLocalDate()) }
            .sortedBy { it.start }
            .forEach { entry ->
                val end = entry.end
                val start = entry.start.atZone(zone)
                appendLine(
                    row(
                        entry.start.toString(),
                        end?.toString().orEmpty(),
                        categories[entry.categoryId]?.name.orEmpty(),
                        projects[entry.projectId]?.name.orEmpty(),
                        entry.note,
                        entry.description,
                        start.toLocalDate().format(DATE),
                        start.toLocalTime().format(TIME),
                        end?.atZone(zone)?.toLocalTime()?.format(TIME).orEmpty(),
                        if (entry.point) "0" else Duration.between(entry.start, end ?: now).toMinutes().toString(),
                        if (entry.point) "yes" else "",
                    )
                )
            }
    }

    fun plannedBlocks(
        plans: List<PlanBlock>,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
        period: ExportPeriod,
    ): String = buildString {
        appendLine(row("title", "category", "project", "first date", "start", "end", "repeats", "reminders (min before)", "description"))
        plans.filter { period.contains(it.startDate) }.sortedBy { it.startDate }.forEach { plan ->
            appendLine(
                row(
                    safe(plan.note),
                    safe(categories[plan.categoryId]?.name.orEmpty()),
                    safe(projects[plan.projectId]?.name.orEmpty()),
                    plan.startDate.format(DATE),
                    plan.start.format(TIME),
                    if (plan.point) "" else plan.end.format(TIME),
                    when (val rule = plan.repeat) {
                        RepeatRule.Once -> "once"
                        RepeatRule.Daily -> "daily"
                        RepeatRule.Monthly -> "monthly"
                        is RepeatRule.Weekly -> "weekly " + rule.days.sorted().joinToString(" ") { it.name.take(3) }
                    },
                    plan.reminders.joinToString(" "),
                    safe(plan.description),
                )
            )
        }
    }

    fun notes(
        notes: List<Note>,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
        period: ExportPeriod,
        zone: ZoneId,
    ): String = buildString {
        appendLine(row("title", "category", "project", "created", "updated", "text"))
        notes.filter { period.contains(it.updated.atZone(zone).toLocalDate()) }
            .sortedBy { it.updated }
            .forEach { note ->
                appendLine(
                    row(
                        safe(note.title),
                        safe(categories[note.categoryId]?.name.orEmpty()),
                        safe(projects[note.projectId]?.name.orEmpty()),
                        note.created.atZone(zone).toLocalDate().format(DATE),
                        note.updated.atZone(zone).toLocalDate().format(DATE),
                        safe(plainText(note.body)),
                    )
                )
            }
    }

    fun projectsAndCategories(
        categories: List<Category>,
        projects: List<Project>,
        entries: List<TimeEntry>,
        now: Instant,
    ): String = buildString {
        appendLine(row("kind", "name", "category", "archived", "tracked minutes"))
        val byCategory = summarize(entries, Instant.MIN.plusSeconds(1), Instant.MAX.minusSeconds(1), now)
            .associate { it.categoryId to it.total }
        val byProject = summarizeByProject(entries, Instant.MIN.plusSeconds(1), Instant.MAX.minusSeconds(1), now).toMap()
        val names = categories.associate { it.id to it.name }
        categories.forEach {
            appendLine(row("category", safe(it.name), "", "", (byCategory[it.id] ?: Duration.ZERO).toMinutes().toString()))
        }
        projects.forEach {
            appendLine(
                row(
                    "project", safe(it.name), safe(names[it.categoryId].orEmpty()),
                    if (it.archived) "yes" else "", (byProject[it.id] ?: Duration.ZERO).toMinutes().toString(),
                )
            )
        }
    }
}
