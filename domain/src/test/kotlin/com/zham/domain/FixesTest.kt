package com.zham.domain

import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.Goal
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.GoalType
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.Project
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import com.zham.domain.model.occurrencesOn
import com.zham.domain.model.occursOn
import com.zham.domain.usecase.BackupData
import com.zham.domain.usecase.BuildBackupUseCase
import com.zham.domain.usecase.CompletePlanUseCase
import com.zham.domain.usecase.Csv
import com.zham.domain.usecase.FullBackup
import com.zham.domain.usecase.RestoreBackupUseCase
import com.zham.domain.usecase.UncompletePlanUseCase
import com.zham.domain.usecase.dayPlan
import com.zham.domain.usecase.summarize
import com.zham.domain.usecase.totalIn
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val UTC = ZoneOffset.UTC

private class Mem {
    val entries = mutableListOf<TimeEntry>()
    val categories = mutableListOf<Category>()
    val projects = mutableListOf<Project>()
    val plans = mutableListOf<PlanBlock>()
    val completions = mutableListOf<PlanCompletion>()
    val goals = mutableListOf<Goal>()
    private var next = 1L

    private fun <T> upsert(list: MutableList<T>, item: T, id: (T) -> Long, withId: (T, Long) -> T) {
        val index = list.indexOfFirst { id(it) == id(item) && id(item) != 0L }
        if (index >= 0) list[index] = item else list += withId(item, next++)
    }

    val time = object : TimeRepository {
        override fun observeEntries(): Flow<List<TimeEntry>> = flow { emit(entries.toList()) }
        override suspend fun findRunning() = entries.firstOrNull { it.end == null }
        override suspend fun findById(id: Long) = entries.firstOrNull { it.id == id }
        override suspend fun save(entry: TimeEntry) = upsert(entries, entry, { it.id }, { e, i -> e.copy(id = i) })
        override suspend fun delete(id: Long) { entries.removeAll { it.id == id } }
    }
    val category = object : CategoryRepository {
        override fun observeCategories(): Flow<List<Category>> = flow { emit(categories.toList()) }
        override suspend fun save(category: Category) = upsert(categories, category, { it.id }, { e, i -> e.copy(id = i) })
        override suspend fun delete(id: Long) { categories.removeAll { it.id == id } }
    }
    val project = object : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = flow { emit(projects.toList()) }
        override suspend fun save(project: Project) = upsert(projects, project, { it.id }, { e, i -> e.copy(id = i) })
        override suspend fun delete(id: Long) { projects.removeAll { it.id == id } }
    }
    val goal = object : GoalRepository {
        override fun observeGoals(): Flow<List<Goal>> = flow { emit(goals.toList()) }
        override suspend fun save(goal: Goal) = upsert(goals, goal, { it.id }, { e, i -> e.copy(id = i) })
        override suspend fun delete(id: Long) { goals.removeAll { it.id == id } }
    }
    val plan = object : PlanRepository {
        override fun observePlans(): Flow<List<PlanBlock>> = flow { emit(plans.toList()) }
        override fun observeCompletions(): Flow<List<PlanCompletion>> = flow { emit(completions.toList()) }
        override suspend fun findById(id: Long) = plans.firstOrNull { it.id == id }
        override suspend fun save(plan: PlanBlock) = upsert(plans, plan, { it.id }, { e, i -> e.copy(id = i) })
        override suspend fun delete(id: Long) { plans.removeAll { it.id == id } }
        override suspend fun setCompleted(planId: Long, date: LocalDate, completed: Boolean) {
            completions.removeAll { it.planId == planId && it.date == date }
            if (completed) completions += PlanCompletion(planId, date)
        }
    }
}

private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDate.of(2026, 1, day).atTime(hour, minute).toInstant(UTC)

class FixesTest {
    @Test
    fun `csv keeps line breaks and commas inside a description`() {
        val entry = TimeEntry(1, null, null, "n", at(1, 10), at(1, 11), "line one\nline two, with comma\r\nthree")
        val rows = Csv.parse(Csv.export(listOf(entry), emptyMap(), emptyMap()))
        rows.size shouldBe 1
        rows.single().description shouldBe "line one\nline two, with comma\r\nthree"
    }

    @Test
    fun `csv still reads a file with windows line endings and a byte order mark`() {
        val text = "﻿start,end,category,project,note,description\r\n2026-01-01T10:00:00Z,2026-01-01T11:00:00Z,Work,,x,\r\n"
        Csv.parse(text).single().category shouldBe "Work"
    }

    @Test
    fun `monthly block set for the 31st falls on the last day of shorter months`() {
        val block = PlanBlock(1, null, null, "", LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalDate.of(2026, 1, 31), RepeatRule.Monthly)
        block.occursOn(LocalDate.of(2026, 2, 28)) shouldBe true
        block.occursOn(LocalDate.of(2026, 2, 27)) shouldBe false
        block.occursOn(LocalDate.of(2026, 4, 30)) shouldBe true
        block.occursOn(LocalDate.of(2026, 3, 31)) shouldBe true
        block.occursOn(LocalDate.of(2026, 3, 30)) shouldBe false
    }

    @Test
    fun `overlapping records are counted once`() {
        val a = TimeEntry(1, 1, null, "", at(1, 10), at(1, 12))
        val b = TimeEntry(2, 1, null, "", at(1, 11), at(1, 13))
        totalIn(listOf(a, b), at(1, 0), at(2, 0), at(2, 0)) shouldBe Duration.ofHours(3)
        summarize(listOf(a, b), at(1, 0), at(2, 0), at(2, 0)).single().total shouldBe Duration.ofHours(3)
    }

    @Test
    fun `a day never tracks more than itself even with overlaps`() {
        val a = TimeEntry(1, 1, null, "", at(1, 0), at(2, 0))
        val b = TimeEntry(2, 2, null, "", at(1, 6), at(1, 18))
        val day = dayPlan(LocalDate.of(2026, 1, 1), emptyList(), listOf(a, b), UTC, at(3, 0))
        day.tracked shouldBe Duration.ofHours(24)
        day.unplanned shouldBe Duration.ofHours(24)
    }

    @Test
    fun `a block past midnight shows its tail on the next day and is split between the days`() {
        val night = PlanBlock(1, 1, null, "", LocalTime.of(23, 0), LocalTime.of(1, 0),
            LocalDate.of(2026, 1, 1), RepeatRule.Daily)
        val day2 = occurrencesOn(listOf(night), LocalDate.of(2026, 1, 2), UTC, emptySet())
        day2.map { it.date } shouldBe listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2))
        val plan = dayPlan(LocalDate.of(2026, 1, 2), day2, emptyList(), UTC, at(3, 0))
        plan.planned shouldBe Duration.ofHours(2) // 00:00-01:00 tail and 23:00-24:00
        val first = dayPlan(LocalDate.of(2026, 1, 1), occurrencesOn(listOf(night), LocalDate.of(2026, 1, 1), UTC, emptySet()), emptyList(), UTC, at(3, 0))
        first.planned shouldBe Duration.ofHours(1)
    }

    @Test
    fun `completing a block twice logs it once and undo removes the record`() = runTest {
        val mem = Mem()
        mem.plan.save(PlanBlock(0, 1, null, "Work", LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalDate.of(2026, 1, 1), RepeatRule.Once))
        val clock = Clock.fixed(at(1, 20), UTC)
        val date = LocalDate.of(2026, 1, 1)
        val planId = mem.plans.single().id

        CompletePlanUseCase(mem.plan, mem.time, clock)(planId, date)
        CompletePlanUseCase(mem.plan, mem.time, clock)(planId, date)
        mem.entries.size shouldBe 1
        mem.completions.size shouldBe 1

        UncompletePlanUseCase(mem.plan, mem.time, clock)(planId, date)
        mem.entries.size shouldBe 0
        mem.completions.size shouldBe 0
    }

    @Test
    fun `undo keeps records that were not made by the block`() = runTest {
        val mem = Mem()
        mem.plan.save(PlanBlock(0, 1, null, "Work", LocalTime.of(9, 0), LocalTime.of(10, 0),
            LocalDate.of(2026, 1, 1), RepeatRule.Once))
        mem.time.save(TimeEntry(0, 1, null, "Something else", at(1, 9), at(1, 10)))
        val clock = Clock.fixed(at(1, 20), UTC)
        UncompletePlanUseCase(mem.plan, mem.time, clock)(mem.plans.single().id, LocalDate.of(2026, 1, 1))
        mem.entries.size shouldBe 1
    }

    @Test
    fun `full backup survives a round trip through json text`() = runTest {
        val source = Mem()
        source.category.save(Category(0, "Work \"main\"", 0xFF112233.toInt(), "code", 1.0))
        val workId = source.categories.single().id
        source.project.save(Project(0, "Site", workId, 0xFF445566.toInt(), true))
        source.time.save(TimeEntry(0, workId, source.projects.single().id, "Note, with comma", at(1, 9), at(1, 10), "a\nb"))
        source.plan.save(PlanBlock(0, workId, null, "Plan", LocalTime.of(9, 0), LocalTime.of(10, 30),
            LocalDate.of(2026, 1, 5), RepeatRule.Weekly(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY)), "d"))
        source.plan.setCompleted(source.plans.single().id, LocalDate.of(2026, 1, 5), true)
        source.goal.save(Goal(0, "Deep work", workId, Duration.ofHours(10), GoalPeriod.Week, GoalType.AtLeast))

        val text = FullBackup.encode(BuildBackupUseCase(source.time, source.category, source.project, source.plan, source.goal)())
        val decoded = FullBackup.decode(text)!!

        val target = Mem()
        val restore = RestoreBackupUseCase(target.time, target.category, target.project, target.plan, target.goal)
        val first = restore(decoded) { 0xFF000000.toInt() }
        first.entries shouldBe 1
        first.plans shouldBe 1
        first.goals shouldBe 1

        target.categories.single().name shouldBe "Work \"main\""
        target.categories.single().icon shouldBe "code"
        target.projects.single().archived shouldBe true
        target.entries.single().description shouldBe "a\nb"
        target.entries.single().note shouldBe "Note, with comma"
        target.plans.single().repeat shouldBe RepeatRule.Weekly(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY))
        target.completions.size shouldBe 1
        target.goals.single().target shouldBe Duration.ofHours(10)

        // Restoring the same file again must not duplicate anything.
        val second = restore(decoded) { 0xFF000000.toInt() }
        second.entries shouldBe 0
        second.plans shouldBe 0
        second.goals shouldBe 0
        target.entries.size shouldBe 1
        target.categories.size shouldBe 1
        target.projects.size shouldBe 1
        target.plans.size shouldBe 1
        target.completions.size shouldBe 1
    }

    @Test
    fun `decode refuses text that is not a zham backup`() {
        FullBackup.decode("not json") shouldBe null
        FullBackup.decode("{\"app\":\"other\"}") shouldBe null
        FullBackup.decode("start,end,category") shouldBe null
        FullBackup.decode("{\"app\":\"zham\",\"categories\":[{\"name\":\"\"}]}")!!.isEmpty shouldBe true
    }

    @Test
    fun `csv import merges with existing records without duplicating`() = runTest {
        val mem = Mem()
        val csv = "start,end,category,project,note,description\n" +
            "2026-01-01T10:00:00Z,2026-01-01T11:00:00Z,Work,,x,\"d1\nd2\"\n"
        val restore = RestoreBackupUseCase(mem.time, mem.category, mem.project, mem.plan, mem.goal)
        restore(FullBackup.fromCsv(csv)) { 0 }.entries shouldBe 1
        val again = restore(FullBackup.fromCsv(csv)) { 0 }
        again.entries shouldBe 0
        again.skipped shouldBe 1
        mem.entries.single().description shouldBe "d1\nd2"
        mem.categories.single().name shouldBe "Work"
    }
}
