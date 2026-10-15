package com.zham.domain.usecase

import com.zham.domain.model.Category
import com.zham.domain.model.Project
import com.zham.domain.model.TimeEntry
import java.time.Instant

/**
 * CSV backup of the entries, one row per entry.
 * Columns: start, end, category, project, note, description — times are ISO-8601 UTC.
 */
object Csv {
    const val HEADER = "start,end,category,project,note,description"

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
                    entry.description,
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
        val description: String = "",
    )

    /**
     * Rows that cannot be parsed are skipped, so one bad line cannot fail the whole import.
     * A quoted cell may hold line breaks, which is how a multi-line description is stored.
     */
    fun parse(csv: String): List<Row> = records(csv.removePrefix("\uFEFF"))
        .drop(1)
        .mapNotNull { cells ->
            if (cells.size < 5) return@mapNotNull null
            val start = runCatching { Instant.parse(cells[0]) }.getOrNull()
                ?: return@mapNotNull null
            Row(
                start = start,
                end = runCatching { Instant.parse(cells[1]) }.getOrNull(),
                category = cells[2],
                project = cells[3],
                note = cells[4],
                description = cells.getOrNull(5).orEmpty(),
            )
        }
        .toList()

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Splits the text into records of cells, honouring quotes, doubled quotes and line breaks. */
    private fun records(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        fun endRecord() {
            cells += current.toString()
            current.clear()
            if (!(cells.size == 1 && cells[0].isEmpty())) result += cells
            cells = mutableListOf()
        }
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                !quoted && char == ',' -> {
                    cells += current.toString()
                    current.clear()
                }
                !quoted && char == '\r' -> Unit
                !quoted && char == '\n' -> endRecord()
                else -> current.append(char)
            }
            index++
        }
        if (current.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return result
    }
}
