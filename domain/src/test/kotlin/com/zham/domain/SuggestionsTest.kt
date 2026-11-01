package com.zham.domain

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.usecase.titleSuggestions
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test

private fun entry(id: Long, category: Long?, project: Long?, note: String, day: Int = 1) = TimeEntry(
    id, category, project, note,
    Instant.parse("2026-01-%02dT10:00:00Z".format(day)), Instant.parse("2026-01-%02dT11:00:00Z".format(day)),
)

class SuggestionsTest {
    private val entries = listOf(
        entry(1, 1, 10, "English lesson", 1),
        entry(2, 1, 10, "english lesson", 2),
        entry(3, 1, 10, "Vocabulary", 3),
        entry(4, 1, 11, "Kotlin course", 4),
        entry(5, 2, null, "Gym", 5),
        entry(6, 1, null, "", 6),
    )

    @Test
    fun `a category offers the titles of all its projects, most used first`() {
        titleSuggestions(entries, emptyList(), categoryId = 1, projectId = null) shouldBe
            listOf("english lesson", "Kotlin course", "Vocabulary")
    }

    @Test
    fun `a project narrows the titles to its own`() {
        titleSuggestions(entries, emptyList(), categoryId = 1, projectId = 11) shouldBe listOf("Kotlin course")
    }

    @Test
    fun `typing filters the list and hides an exact match`() {
        titleSuggestions(entries, emptyList(), 1, null, query = "voc") shouldBe listOf("Vocabulary")
        titleSuggestions(entries, emptyList(), 1, null, query = "Vocabulary") shouldBe emptyList()
    }

    @Test
    fun `planned block titles count too`() {
        val plan = PlanBlock(1, 3, null, "Weekly review", LocalTime.of(9, 0), LocalTime.of(10, 0), LocalDate.of(2026, 1, 1), RepeatRule.Once)
        titleSuggestions(emptyList(), listOf(plan), categoryId = 3, projectId = null) shouldBe listOf("Weekly review")
    }

    @Test
    fun `with nothing chosen every title is offered`() {
        titleSuggestions(entries, emptyList(), null, null).size shouldBe 4
    }
}
