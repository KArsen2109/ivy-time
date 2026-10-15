package com.ivy.time.domain

import arrow.core.left
import arrow.core.right
import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.PlannedEntry
import com.ivy.time.domain.model.PlannedRepository
import com.ivy.time.domain.model.Recurrence
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeError
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.CategoryTotal
import com.ivy.time.domain.usecase.CompletePlannedUseCase
import com.ivy.time.domain.usecase.Csv
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.domain.usecase.SaveEntryUseCase
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import com.ivy.time.domain.usecase.dailyTotals
import com.ivy.time.domain.usecase.formatHms
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.goalProgress
import com.ivy.time.domain.usecase.periodRange
import com.ivy.time.domain.usecase.summarize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val T0: Instant = Instant.parse("2024-01-03T10:00:00Z") // a Wednesday

private class FakeTimeRepo : TimeRepository {
    val saved = mutableListOf<TimeEntry>()
    override fun observeEntries(): Flow<List<TimeEntry>> = emptyFlow()
    override suspend fun findRunning() = saved.lastOrNull { it.isRunning }
    override suspend fun findById(id: Long) = saved.firstOrNull { it.id == id }
    override suspend fun save(entry: TimeEntry) {
        if (entry.id == 0L) {
            saved += entry.copy(id = saved.size + 1L)
        } else {
            saved[saved.indexOfFirst { it.id == entry.id }] = entry
        }
    }
    override suspend fun delete(id: Long) { saved.removeAll { it.id == id } }
}

private class FakePlannedRepo(initial: List<PlannedEntry>) : PlannedRepository {
    val saved = initial.toMutableList()
    override fun observePlanned(): Flow<List<PlannedEntry>> = emptyFlow()
    override suspend fun findById(id: Long) = saved.firstOrNull { it.id == id }
    override suspend fun save(planned: PlannedEntry) {
        val index = saved.indexOfFirst { it.id == planned.id }
        if (index >= 0) saved[index] = planned else saved += planned
    }
    override suspend fun delete(id: Long) { saved.removeAll { it.id == id } }
}

class DomainTest {
    private val clock = Clock.fixed(T0, ZoneOffset.UTC)

    @Test
    fun `start timer saves a running entry`() = runTest {
        val repo = FakeTimeRepo()
        StartTimerUseCase(repo, clock)(3, 7, " Write ") shouldBe Unit.right()
        repo.saved.single() shouldBe TimeEntry(1, 3, 7, "Write", T0, null)
    }

    @Test
    fun `cannot start a second timer`() = runTest {
        val repo = FakeTimeRepo()
        val start = StartTimerUseCase(repo, clock)
        start(null, null, "a")
        start(null, null, "b") shouldBe TimeError.TimerAlreadyRunning.left()
        repo.saved.size shouldBe 1
    }

    @Test
    fun `stop timer sets end to now`() = runTest {
        val repo = FakeTimeRepo()
        StartTimerUseCase(repo, clock)(null, null, "")
        StopTimerUseCase(repo, Clock.fixed(T0.plusSeconds(90), ZoneOffset.UTC))() shouldBe Unit.right()
        repo.saved.single().end shouldBe T0.plusSeconds(90)
    }

    @Test
    fun `stop without timer fails`() = runTest {
        StopTimerUseCase(FakeTimeRepo(), clock)() shouldBe TimeError.NoRunningTimer.left()
    }

    @Test
    fun `manual entry keeps the given start and end`() = runTest {
        val repo = FakeTimeRepo()
        val save = SaveEntryUseCase(repo, clock)
        val start = T0.minusSeconds(5 * 3600)
        save(0, 1, null, "Work", start, T0) shouldBe Unit.right()
        repo.saved.single().duration(T0) shouldBe Duration.ofHours(5)
    }

    @Test
    fun `manual entry rejects a bad range and the future`() = runTest {
        val save = SaveEntryUseCase(FakeTimeRepo(), clock)
        save(0, null, null, "", T0, T0) shouldBe TimeError.EndNotAfterStart.left()
        save(0, null, null, "", T0, T0.plusSeconds(60)) shouldBe TimeError.EndInTheFuture.left()
    }

    @Test
    fun `completing a one-time plan logs it and removes the plan`() = runTest {
        val planned = PlannedEntry(
            id = 1,
            categoryId = 2,
            projectId = null,
            note = "Gym",
            duration = Duration.ofHours(1),
            date = LocalDate.of(2024, 1, 3),
            time = LocalTime.of(8, 0),
            recurrence = Recurrence.OneTime,
        )
        val timeRepo = FakeTimeRepo()
        val plannedRepo = FakePlannedRepo(listOf(planned))
        CompletePlannedUseCase(plannedRepo, timeRepo, clock)(1) shouldBe Unit.right()

        timeRepo.saved.single().let {
            it.categoryId shouldBe 2
            it.start shouldBe Instant.parse("2024-01-03T08:00:00Z")
            it.end shouldBe Instant.parse("2024-01-03T09:00:00Z")
        }
        plannedRepo.saved shouldBe emptyList()
    }

    @Test
    fun `completing a recurring plan moves it to the next week`() = runTest {
        val planned = PlannedEntry(
            id = 1,
            categoryId = null,
            projectId = null,
            note = "Standup",
            duration = Duration.ofMinutes(30),
            date = LocalDate.of(2024, 1, 3),
            time = LocalTime.of(9, 0),
            recurrence = Recurrence.Weekly,
        )
        val plannedRepo = FakePlannedRepo(listOf(planned))
        CompletePlannedUseCase(plannedRepo, FakeTimeRepo(), clock)(1)
        plannedRepo.saved.single().date shouldBe LocalDate.of(2024, 1, 10)
    }

    @Test
    fun `a plan that is still ahead logs only the time that has passed`() = runTest {
        val planned = PlannedEntry(
            id = 1,
            categoryId = null,
            projectId = null,
            note = "Deep work",
            duration = Duration.ofHours(4),
            date = LocalDate.of(2024, 1, 3),
            time = LocalTime.of(9, 0), // 9:00 + 4h = 13:00, but now is 10:00
            recurrence = Recurrence.OneTime,
        )
        val timeRepo = FakeTimeRepo()
        CompletePlannedUseCase(FakePlannedRepo(listOf(planned)), timeRepo, clock)(1)
        timeRepo.saved.single().end shouldBe T0
    }

    @Test
    fun `summarize clips to range, counts running and sorts`() {
        val entries = listOf(
            TimeEntry(1, 1, null, "", T0.minusSeconds(3600), T0.plusSeconds(1800)),
            TimeEntry(2, 2, null, "", T0.plusSeconds(3600), T0.plusSeconds(3600 + 7200)),
            TimeEntry(3, 1, null, "", T0.plusSeconds(10_000), null),
        )
        summarize(entries, T0, T0.plusSeconds(100_000), T0.plusSeconds(10_100)) shouldBe listOf(
            CategoryTotal(2, Duration.ofHours(2)),
            CategoryTotal(1, Duration.ofSeconds(1800 + 100)),
        )
    }

    @Test
    fun `period ranges start on the right day`() {
        val wednesday = LocalDate.of(2024, 1, 3)
        periodRange(PeriodType.Week, wednesday).from shouldBe LocalDate.of(2024, 1, 1)
        periodRange(PeriodType.Month, wednesday).from shouldBe LocalDate.of(2024, 1, 1)
        periodRange(PeriodType.Day, wednesday).toExclusive shouldBe LocalDate.of(2024, 1, 4)
    }

    @Test
    fun `daily totals keep empty days`() {
        val entries = listOf(TimeEntry(1, null, null, "", T0, T0.plusSeconds(3600)))
        val range = periodRange(PeriodType.Week, LocalDate.of(2024, 1, 3))
        val totals = dailyTotals(entries, range, ZoneOffset.UTC, T0)
        totals.size shouldBe 7
        totals.first { it.first == LocalDate.of(2024, 1, 3) }.second shouldBe Duration.ofHours(1)
        totals.first { it.first == LocalDate.of(2024, 1, 4) }.second shouldBe Duration.ZERO
    }

    @Test
    fun `at-least goal is met only once the target is reached`() {
        val goal = Goal(1, "Sport", 5, Duration.ofHours(3), GoalPeriod.Week, GoalType.AtLeast)
        val entries = listOf(TimeEntry(1, 5, null, "", T0.minusSeconds(7200), T0))
        val progress = goalProgress(goal, entries, LocalDate.of(2024, 1, 3), ZoneOffset.UTC, T0)
        progress.spent shouldBe Duration.ofHours(2)
        progress.met shouldBe false
    }

    @Test
    fun `at-most goal only counts its own category`() {
        val goal = Goal(1, "Less social", 9, Duration.ofHours(1), GoalPeriod.Week, GoalType.AtMost)
        val entries = listOf(TimeEntry(1, 2, null, "", T0.minusSeconds(7200), T0))
        goalProgress(goal, entries, LocalDate.of(2024, 1, 3), ZoneOffset.UTC, T0).met shouldBe true
    }

    @Test
    fun `csv survives a round trip with commas and quotes`() {
        val entries = listOf(TimeEntry(1, null, null, "a,b \"c\"", T0, T0.plusSeconds(60)))
        val csv = Csv.export(entries, emptyMap(), emptyMap())
        val rows = Csv.parse(csv)
        rows.single().note shouldBe "a,b \"c\""
        rows.single().start shouldBe T0
    }

    @Test
    fun `csv import skips broken lines`() {
        val csv = Csv.HEADER + "\nnot-a-date,,,,\n" + "${T0},,Work,,hi\n"
        Csv.parse(csv).single().category shouldBe "Work"
    }

    @Test
    fun `duration formatting`() {
        Duration.ofSeconds(3725).formatHms() shouldBe "1:02:05"
        Duration.ofMinutes(125).formatShort() shouldBe "2h 05m"
        Duration.ofMinutes(45).formatShort() shouldBe "45m"
    }
}
