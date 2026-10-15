package com.zham.domain

import com.zham.domain.model.Goal
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalType
import com.zham.domain.model.TimeEntry
import com.zham.domain.usecase.counts
import com.zham.domain.usecase.goalProgress
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test

private fun rec(id: Long, category: Long?, project: Long?, note: String, hours: Long = 1) = TimeEntry(
    id, category, project, note,
    Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-05T09:00:00Z").plus(Duration.ofHours(hours)),
)

private fun goal(
    category: Long? = 1, project: Long? = null, title: Boolean = false, name: String = "English",
) = Goal(1, name, category, Duration.ofHours(5), GoalPeriod.Week, GoalType.AtLeast, project, title)

class GoalScopeTest {
    private val english = rec(1, category = 1, project = 10, note = "")
    private val coding = rec(2, category = 1, project = 11, note = "")
    private val untitled = rec(3, category = 1, project = null, note = "")
    private val titled = rec(4, category = 1, project = null, note = "english")
    private val other = rec(5, category = 2, project = null, note = "English")

    @Test
    fun `a project goal counts only that project`() {
        val g = goal(project = 10)
        listOf(english, coding, untitled, titled, other).filter(g::counts) shouldBe listOf(english)
    }

    @Test
    fun `a title goal counts records of the category with the same title`() {
        val g = goal(title = true)
        listOf(english, coding, untitled, titled, other).filter(g::counts) shouldBe listOf(titled)
    }

    @Test
    fun `a whole category goal still counts everything in it`() {
        listOf(english, coding, untitled, titled, other).filter(goal()::counts).map { it.id } shouldBe listOf(1L, 2L, 3L, 4L)
    }

    @Test
    fun `a goal without a category counts everything`() {
        listOf(english, other).filter(goal(category = null)::counts).size shouldBe 2
    }

    @Test
    fun `progress follows the project`() {
        val entries = listOf(english, coding)
        val progress = goalProgress(
            goal(project = 10), entries, LocalDate.of(2026, 1, 7), ZoneOffset.UTC, Instant.parse("2026-01-08T00:00:00Z"),
        )
        progress.spent shouldBe Duration.ofHours(1)
    }
}
