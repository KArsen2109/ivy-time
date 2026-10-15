package com.ivy.time.domain.usecase

import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.TimeEntry
import java.time.Instant

/**
 * CSV backup of the entries, one row per entry, same idea as Ivy Wallet's export.
 * Columns: start, end, category, project, note — times are ISO-8601 UTC.
 */
object Csv {
    const val HEADER = "start,end,category,project,note"

    fun export(
        entries: List<TimeEntry>,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
    ): String = buildString {
        appendLine(HEADER)
        entries.sortedBy { it.start }.forEach { entry ->
            appendLine(
                listOf(
                    entry.start.toString(),
                    entry.end?.toString().orEmpty(),
                    categories[entry.categoryId]?.name.orEmpty(),
                    projects[entry.projectId]?.name.orEmpty(),
                    entry.note,
                ).joinToString(",") { escape(it) }
            )
        }
    }

    data class Row(
        val start: Instant,
        val end: Instant?,
        val category: String,
        val project: String,
        val note: String,
    )

    /** Rows that cannot be parsed are skipped, so one bad line cannot fail the whole import. */
    fun parse(csv: String): List<Row> = csv.lineSequence()
        .drop(1)
        .mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val cells = splitLine(line)
            if (cells.size < 5) return@mapNotNull null
            val start = runCatching { Instant.parse(cells[0]) }.getOrNull()
                ?: return@mapNotNull null
            Row(
                start = start,
                end = runCatching { Instant.parse(cells[1]) }.getOrNull(),
                category = cells[2],
                project = cells[3],
                note = cells[4],
            )
        }
        .toList()

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun splitLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        cells += current.toString()
        return cells
    }
}
