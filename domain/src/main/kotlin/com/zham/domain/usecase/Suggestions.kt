package com.zham.domain.usecase

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.TimeEntry
import java.time.Instant

/**
 * Titles used before, most used first, to offer one tap instead of typing again.
 *
 * With a project chosen only that project's titles count; with just a category, the titles of the
 * whole category; with neither, everything. Titles that merely differ in letter case are one title.
 * [query] narrows the list as the user types, and a title equal to what is typed is left out.
 */
fun titleSuggestions(
    entries: List<TimeEntry>,
    plans: List<PlanBlock>,
    categoryId: Long?,
    projectId: Long?,
    query: String = "",
    limit: Int = 12,
): List<String> {
    class Seen(var spelling: String, var count: Int, var last: Instant)

    val byKey = linkedMapOf<String, Seen>()
    fun add(title: String, category: Long?, project: Long?, at: Instant) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val matches = when {
            projectId != null -> project == projectId
            categoryId != null -> category == categoryId
            else -> true
        }
        if (!matches) return
        val seen = byKey.getOrPut(clean.lowercase()) { Seen(clean, 0, Instant.MIN) }
        seen.count++
        if (at > seen.last) {
            seen.last = at
            seen.spelling = clean
        }
    }
    entries.forEach { add(it.note, it.categoryId, it.projectId, it.start) }
    plans.forEach { add(it.note, it.categoryId, it.projectId, Instant.MIN) }

    val typed = query.trim().lowercase()
    return byKey.values
        .filter { typed.isEmpty() || (it.spelling.lowercase().contains(typed) && it.spelling.lowercase() != typed) }
        .sortedWith(compareByDescending<Seen> { it.count }.thenByDescending { it.last })
        .take(limit)
        .map { it.spelling }
}
