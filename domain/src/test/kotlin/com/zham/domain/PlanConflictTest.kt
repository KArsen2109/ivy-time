package com.zham.domain

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.planConflicts
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test

private val Z = ZoneOffset.UTC
private val D = LocalDate.of(2026, 1, 5)

private fun block(
    id: Long, from: Int, to: Int, date: LocalDate = D, repeat: RepeatRule = RepeatRule.Once,
    fromMinute: Int = 0, toMinute: Int = 0,
) = PlanBlock(id, null, null, "b$id", LocalTime.of(from, fromMinute), LocalTime.of(to, toMinute), date, repeat)

class PlanConflictTest {
    @Test
    fun `blocks that share time are reported once with the day they meet`() {
        val found = planConflicts(block(0, 9, 11), listOf(block(1, 10, 12), block(2, 13, 14)), Z)
        found.map { it.other.id } shouldBe listOf(1L)
        found.single().date shouldBe D
    }

    @Test
    fun `blocks that only touch do not conflict`() {
        planConflicts(block(0, 9, 10), listOf(block(1, 10, 11)), Z) shouldBe emptyList()
    }

    @Test
    fun `a block does not conflict with itself when edited`() {
        planConflicts(block(1, 9, 11), listOf(block(1, 9, 11)), Z) shouldBe emptyList()
    }

    @Test
    fun `a repeating block finds a clash on a later day`() {
        val later = block(1, 9, 10, date = D.plusDays(3))
        val found = planConflicts(block(0, 9, 10, repeat = RepeatRule.Daily), listOf(later), Z)
        found.single().date shouldBe D.plusDays(3)
    }

    @Test
    fun `the tail of a block past midnight clashes with an early block next day`() {
        val night = block(1, 23, 1, date = D)
        val early = block(0, 0, 1, date = D.plusDays(1), fromMinute = 30, toMinute = 0)
        planConflicts(early, listOf(night), Z).map { it.other.id } shouldBe listOf(1L)
    }
}
