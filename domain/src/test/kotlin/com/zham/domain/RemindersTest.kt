package com.zham.domain

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.RepeatRule
import com.zham.domain.usecase.nextReminder
import com.zham.domain.usecase.remindersBetween
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val Z = ZoneOffset.UTC
private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
    LocalDate.of(2026, 1, day).atTime(hour, minute).toInstant(Z)

private fun plan(
    id: Long = 1,
    start: LocalTime = LocalTime.of(14, 30),
    date: LocalDate = LocalDate.of(2026, 1, 10),
    repeat: RepeatRule = RepeatRule.Once,
    reminder: Int? = 15,
) = PlanBlock(id, null, null, "x", start, start.plusHours(1), date, repeat, "", reminder)

class RemindersTest {
    @Test
    fun `a block without a reminder never reminds`() {
        remindersBetween(listOf(plan(reminder = null)), emptySet(), at(1, 0), at(20, 0), Z) shouldBe emptyList()
    }

    @Test
    fun `reminder fires the chosen time before the start`() {
        val next = nextReminder(listOf(plan()), emptySet(), at(10, 0), Z)!!
        next.triggerAt shouldBe at(10, 14, 15)
        next.occurrence.startsAt shouldBe at(10, 14, 30)
    }

    @Test
    fun `zero minutes means at the start`() {
        nextReminder(listOf(plan(reminder = 0)), emptySet(), at(10, 0), Z)!!.triggerAt shouldBe at(10, 14, 30)
    }

    @Test
    fun `a reminder that already passed is not returned`() {
        nextReminder(listOf(plan()), emptySet(), at(10, 14, 20), Z) shouldBe null
    }

    @Test
    fun `a day-long lead reaches back to the day before`() {
        val next = nextReminder(listOf(plan(reminder = 1440)), emptySet(), at(9, 0), Z)!!
        next.triggerAt shouldBe at(9, 14, 30)
    }

    @Test
    fun `daily block gives one reminder per day`() {
        val list = remindersBetween(listOf(plan(repeat = RepeatRule.Daily)), emptySet(), at(10, 0), at(13, 0), Z)
        list.map { it.triggerAt } shouldBe listOf(at(10, 14, 15), at(11, 14, 15), at(12, 14, 15))
    }

    @Test
    fun `a ticked-off occurrence does not remind`() {
        val done = setOf(PlanCompletion(1, LocalDate.of(2026, 1, 10)))
        nextReminder(listOf(plan()), done, at(10, 0), Z) shouldBe null
    }

    @Test
    fun `reminders from several blocks come earliest first`() {
        val list = remindersBetween(
            listOf(plan(id = 1), plan(id = 2, start = LocalTime.of(9, 0))), emptySet(), at(10, 0), at(11, 0), Z,
        )
        list.map { it.occurrence.plan.id } shouldBe listOf(2L, 1L)
    }
}
