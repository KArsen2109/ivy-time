package com.zham.domain

import arrow.core.right
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import com.zham.domain.model.occurrencesOn
import com.zham.domain.usecase.CompletePlanUseCase
import com.zham.domain.usecase.dayPlan
import com.zham.domain.usecase.minutesFromMidnight
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val MONDAY: LocalDate = LocalDate.of(2024, 1, 1)
private val ZONE = ZoneOffset.UTC

private fun block(
    id: Long = 1,
    categoryId: Long? = 1,
    start: LocalTime = LocalTime.of(9, 0),
    end: LocalTime = LocalTime.of(11, 0),
    startDate: LocalDate = MONDAY,
    repeat: RepeatRule = RepeatRule.Once,
) = PlanBlock(id, categoryId, null, "Work", start, end, startDate, repeat)

private fun entry(
    id: Long = 1,
    categoryId: Long? = 1,
    fromHour: Int,
    toHour: Int,
    date: LocalDate = MONDAY,
) = TimeEntry(
    id = id,
    categoryId = categoryId,
    projectId = null,
    note = "",
    start = date.atTime(fromHour, 0).toInstant(ZONE),
    end = date.atTime(toHour, 0).toInstant(ZONE),
)

private class FakePlanRepo(private val plans: List<PlanBlock>) : PlanRepository {
    val completed = mutableListOf<PlanCompletion>()
    override fun observePlans(): Flow<List<PlanBlock>> = emptyFlow()
    override fun observeCompletions(): Flow<List<PlanCompletion>> = emptyFlow()
    override suspend fun findById(id: Long) = plans.firstOrNull { it.id == id }
    override suspend fun save(plan: PlanBlock) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun setCompleted(planId: Long, date: LocalDate, completed: Boolean) {
        if (completed) this.completed += PlanCompletion(planId, date)
    }
}

private class RecordingTimeRepo : TimeRepository {
    val saved = mutableListOf<TimeEntry>()
    override fun observeEntries(): Flow<List<TimeEntry>> = emptyFlow()
    override suspend fun findRunning(): TimeEntry? = null
    override suspend fun findById(id: Long): TimeEntry? = null
    override suspend fun save(entry: TimeEntry) { saved += entry }
    override suspend fun delete(id: Long) = Unit
}

class PlanTest {

    @Test
    fun `a one-off block lands only on its own day`() {
        val plans = listOf(block())
        occurrencesOn(plans, MONDAY, ZONE, emptySet()).size shouldBe 1
        occurrencesOn(plans, MONDAY.plusDays(1), ZONE, emptySet()) shouldBe emptyList()
    }

    @Test
    fun `a weekly block lands on its chosen weekdays only`() {
        val plans = listOf(
            block(repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        )
        occurrencesOn(plans, MONDAY, ZONE, emptySet()).size shouldBe 1
        occurrencesOn(plans, MONDAY.plusDays(1), ZONE, emptySet()) shouldBe emptyList()
        occurrencesOn(plans, MONDAY.plusDays(2), ZONE, emptySet()).size shouldBe 1
        occurrencesOn(plans, MONDAY.plusWeeks(3), ZONE, emptySet()).size shouldBe 1
    }

    @Test
    fun `a block never lands before the day it starts`() {
        val plans = listOf(block(startDate = MONDAY, repeat = RepeatRule.Daily))
        occurrencesOn(plans, MONDAY.minusDays(1), ZONE, emptySet()) shouldBe emptyList()
        occurrencesOn(plans, MONDAY.plusDays(10), ZONE, emptySet()).size shouldBe 1
    }

    @Test
    fun `a monthly block keeps its day of the month`() {
        val plans = listOf(block(startDate = LocalDate.of(2024, 1, 15), repeat = RepeatRule.Monthly))
        occurrencesOn(plans, LocalDate.of(2024, 2, 15), ZONE, emptySet()).size shouldBe 1
        occurrencesOn(plans, LocalDate.of(2024, 2, 16), ZONE, emptySet()) shouldBe emptyList()
    }

    @Test
    fun `a completed occurrence is marked done on that day only`() {
        val plans = listOf(block(repeat = RepeatRule.Daily))
        val completed = setOf(PlanCompletion(1, MONDAY))
        occurrencesOn(plans, MONDAY, ZONE, completed).single().done shouldBe true
        occurrencesOn(plans, MONDAY.plusDays(1), ZONE, completed).single().done shouldBe false
    }

    @Test
    fun `a block running past midnight keeps its length`() {
        block(start = LocalTime.of(23, 0), end = LocalTime.of(1, 0)).duration shouldBe
            Duration.ofHours(2)
    }

    @Test
    fun `a day of nothing planned has no adherence`() {
        val plan = dayPlan(MONDAY, emptyList(), listOf(entry(fromHour = 9, toHour = 10)), ZONE, NOW)
        plan.planned shouldBe Duration.ZERO
        plan.adherence shouldBe 0f
        plan.unplanned shouldBe Duration.ofHours(1)
    }

    @Test
    fun `time spent inside the planned window counts as kept`() {
        val occurrences = occurrencesOn(listOf(block()), MONDAY, ZONE, emptySet())
        val plan = dayPlan(MONDAY, occurrences, listOf(entry(fromHour = 9, toHour = 10)), ZONE, NOW)
        plan.planned shouldBe Duration.ofHours(2)
        plan.kept shouldBe Duration.ofHours(1)
        plan.adherence shouldBe 0.5f
        plan.unplanned shouldBe Duration.ZERO
    }

    @Test
    fun `the same hours under a different category do not count as kept`() {
        val occurrences = occurrencesOn(listOf(block(categoryId = 1)), MONDAY, ZONE, emptySet())
        val plan = dayPlan(
            date = MONDAY,
            occurrences = occurrences,
            allEntries = listOf(entry(categoryId = 2, fromHour = 9, toHour = 11)),
            zone = ZONE,
            now = NOW,
        )
        plan.kept shouldBe Duration.ZERO
        // The window was planned, so the time is not counted as unplanned either.
        plan.unplanned shouldBe Duration.ZERO
    }

    @Test
    fun `overlapping entries are not counted twice`() {
        val occurrences = occurrencesOn(listOf(block()), MONDAY, ZONE, emptySet())
        val plan = dayPlan(
            date = MONDAY,
            occurrences = occurrences,
            allEntries = listOf(
                entry(id = 1, fromHour = 9, toHour = 11),
                entry(id = 2, fromHour = 9, toHour = 10),
            ),
            zone = ZONE,
            now = NOW,
        )
        plan.kept shouldBe Duration.ofHours(2)
    }

    @Test
    fun `work outside every planned window is unplanned`() {
        val occurrences = occurrencesOn(listOf(block()), MONDAY, ZONE, emptySet())
        val plan = dayPlan(
            date = MONDAY,
            occurrences = occurrences,
            allEntries = listOf(entry(fromHour = 14, toHour = 16)),
            zone = ZONE,
            now = NOW,
        )
        plan.unplanned shouldBe Duration.ofHours(2)
        plan.kept shouldBe Duration.ZERO
    }

    @Test
    fun `completing a block logs exactly its window`() = runTest {
        val planRepo = FakePlanRepo(listOf(block()))
        val timeRepo = RecordingTimeRepo()
        val clock = Clock.fixed(MONDAY.atTime(18, 0).toInstant(ZONE), ZONE)

        CompletePlanUseCase(planRepo, timeRepo, clock)(1, MONDAY) shouldBe Unit.right()

        timeRepo.saved.single().let {
            it.start shouldBe MONDAY.atTime(9, 0).toInstant(ZONE)
            it.end shouldBe MONDAY.atTime(11, 0).toInstant(ZONE)
        }
        planRepo.completed.single() shouldBe PlanCompletion(1, MONDAY)
    }

    @Test
    fun `completing a block early logs only the time that has passed`() = runTest {
        val timeRepo = RecordingTimeRepo()
        val clock = Clock.fixed(MONDAY.atTime(10, 0).toInstant(ZONE), ZONE)

        CompletePlanUseCase(FakePlanRepo(listOf(block())), timeRepo, clock)(1, MONDAY)

        timeRepo.saved.single().end shouldBe MONDAY.atTime(10, 0).toInstant(ZONE)
    }

    @Test
    fun `minutes from midnight place a block on the hour ruler`() {
        minutesFromMidnight(MONDAY.atTime(9, 30).toInstant(ZONE), MONDAY, ZONE) shouldBe 570
    }

    private companion object {
        val NOW: Instant = MONDAY.atTime(23, 0).toInstant(ZONE)
    }
}
