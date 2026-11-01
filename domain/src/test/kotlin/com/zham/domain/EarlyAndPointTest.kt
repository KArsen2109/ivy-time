package com.zham.domain

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.occurrencesOn
import com.zham.domain.usecase.CompletePlanUseCase
import com.zham.domain.usecase.SaveEntryUseCase
import com.zham.domain.usecase.SavePlanUseCase
import com.zham.domain.usecase.UncompletePlanUseCase
import com.zham.domain.usecase.dayBalance
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val Z = ZoneOffset.UTC
private val DAY = LocalDate.of(2026, 1, 5)
private fun at(h: Int, m: Int = 0): Instant = DAY.atTime(h, m).toInstant(Z)

class EarlyAndPointTest {
    private suspend fun mem(point: Boolean = false, from: Int = 15, to: Int = 16): Pair<Mem, Long> {
        val mem = Mem()
        mem.plan.save(
            PlanBlock(0, 1, null, "Task", LocalTime.of(from, 0), LocalTime.of(to, 0), DAY, RepeatRule.Once, point = point)
        )
        return mem to mem.plans.single().id
    }

    @Test
    fun `a block done ahead of time records the real start and end`() = runTest {
        val (mem, id) = mem()
        val clock = Clock.fixed(at(10), Z) // the block is planned for 15:00 but done at 10:00
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY, at(9, 15), at(10)).isRight() shouldBe true
        val entry = mem.entries.single()
        entry.start shouldBe at(9, 15)
        entry.end shouldBe at(10)
        entry.planId shouldBe id
        mem.completions.size shouldBe 1
    }

    @Test
    fun `done ahead of time without real times is refused`() = runTest {
        val (mem, id) = mem()
        CompletePlanUseCase(mem.plan, mem.time, Clock.fixed(at(10), Z))(id, DAY).isLeft() shouldBe true
        mem.entries.size shouldBe 0
    }

    @Test
    fun `real times must end after they start and not in the future`() = runTest {
        val (mem, id) = mem()
        val clock = Clock.fixed(at(10), Z)
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY, at(10), at(9)).isLeft() shouldBe true
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY, at(9), at(11)).isLeft() shouldBe true
    }

    @Test
    fun `undo removes a record made at real times`() = runTest {
        val (mem, id) = mem()
        val clock = Clock.fixed(at(10), Z)
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY, at(9, 15), at(10))
        UncompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY)
        mem.entries.size shouldBe 0
        mem.completions.size shouldBe 0
    }

    @Test
    fun `a task without length is logged as a moment, now`() = runTest {
        val (mem, id) = mem(point = true, from = 18, to = 18)
        val clock = Clock.fixed(at(17, 30), Z)
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY)
        val entry = mem.entries.single()
        entry.point shouldBe true
        entry.start shouldBe at(17, 30)
        entry.end shouldBe at(17, 30)
    }

    @Test
    fun `a task without length may start and end at the same time`() = runTest {
        val mem = Mem()
        SavePlanUseCase(mem.plan)(0, 1, null, "Call", LocalTime.of(18, 0), LocalTime.of(18, 0), DAY, RepeatRule.Once, point = true)
            .isRight() shouldBe true
        SavePlanUseCase(mem.plan)(0, 1, null, "Block", LocalTime.of(18, 0), LocalTime.of(18, 0), DAY, RepeatRule.Once)
            .isLeft() shouldBe true
        mem.plans.single().duration shouldBe Duration.ZERO
        occurrencesOn(mem.plans, DAY, Z, emptySet()).single().let { it.endsAt shouldBe it.startsAt }
    }

    @Test
    fun `a moment can be logged by hand but not in the future`() = runTest {
        val mem = Mem()
        val clock = Clock.fixed(at(12), Z)
        SaveEntryUseCase(mem.time, clock)(0, 1, null, "Call mum", at(11), at(11), point = true).isRight() shouldBe true
        SaveEntryUseCase(mem.time, clock)(0, 1, null, "Later", at(13), at(13), point = true).isLeft() shouldBe true
        mem.entries.single().point shouldBe true
    }

    @Test
    fun `editing a record made from a block keeps the link`() = runTest {
        val (mem, id) = mem()
        val clock = Clock.fixed(at(10), Z)
        CompletePlanUseCase(mem.plan, mem.time, clock)(id, DAY, at(9), at(10))
        val made = mem.entries.single()
        SaveEntryUseCase(mem.time, clock)(made.id, 1, null, "Edited", at(9), at(10))
        mem.entries.single().planId shouldBe id
    }

    @Test
    fun `the day balance counts what is left and what is still free`() {
        val block = PlanBlock(1, null, null, "", LocalTime.of(14, 0), LocalTime.of(16, 0), DAY, RepeatRule.Once)
        val overlap = PlanBlock(2, null, null, "", LocalTime.of(15, 0), LocalTime.of(17, 0), DAY, RepeatRule.Once)
        val occurrences = occurrencesOn(listOf(block, overlap), DAY, Z, emptySet<PlanCompletion>())
        val balance = dayBalance(DAY, occurrences, Z, at(12))
        balance.left shouldBe Duration.ofHours(12)
        balance.free shouldBe Duration.ofHours(9) // 14:00-17:00 is claimed once
        dayBalance(DAY.plusDays(1), emptyList(), Z, at(12)).left shouldBe Duration.ofHours(24)
        dayBalance(DAY.minusDays(1), emptyList(), Z, at(12)).left shouldBe Duration.ZERO
    }
}
