package com.ivy.time.domain

import arrow.core.left
import arrow.core.right
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeError
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.LogTimeUseCase
import com.ivy.time.domain.usecase.ProjectTotal
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import com.ivy.time.domain.usecase.formatHms
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.summarize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

private val T0 = Instant.parse("2024-01-01T10:00:00Z")

private class FakeRepo : TimeRepository {
    val saved = mutableListOf<TimeEntry>()
    override fun observeEntries(): Flow<List<TimeEntry>> = emptyFlow()
    override suspend fun findRunning() = saved.lastOrNull { it.isRunning }
    override suspend fun save(entry: TimeEntry) {
        if (entry.id == 0L) {
            saved += entry.copy(id = saved.size + 1L)
        } else {
            saved[saved.indexOfFirst { it.id == entry.id }] = entry
        }
    }
    override suspend fun delete(id: Long) { saved.removeAll { it.id == id } }
}

class DomainTest {
    private val clock = Clock.fixed(T0, ZoneOffset.UTC)

    @Test
    fun `start timer saves a running entry`() = runTest {
        val repo = FakeRepo()
        StartTimerUseCase(repo, clock)(7, " Write ") shouldBe Unit.right()
        repo.saved.single() shouldBe TimeEntry(1, 7, "Write", T0, null)
    }

    @Test
    fun `cannot start a second timer`() = runTest {
        val repo = FakeRepo()
        val start = StartTimerUseCase(repo, clock)
        start(null, "a")
        start(null, "b") shouldBe TimeError.TimerAlreadyRunning.left()
        repo.saved.size shouldBe 1
    }

    @Test
    fun `stop timer sets end to now`() = runTest {
        val repo = FakeRepo()
        StartTimerUseCase(repo, Clock.fixed(T0, ZoneOffset.UTC))(null, "")
        val later = Clock.fixed(T0.plusSeconds(90), ZoneOffset.UTC)
        StopTimerUseCase(repo, later)() shouldBe Unit.right()
        repo.saved.single().end shouldBe T0.plusSeconds(90)
    }

    @Test
    fun `stop without timer fails`() = runTest {
        StopTimerUseCase(FakeRepo(), clock)() shouldBe TimeError.NoRunningTimer.left()
    }

    @Test
    fun `log time ends now and rejects non positive durations`() = runTest {
        val repo = FakeRepo()
        val log = LogTimeUseCase(repo, clock)
        log(null, "x", Duration.ofMinutes(30)) shouldBe Unit.right()
        repo.saved.single().start shouldBe T0.minusSeconds(1800)
        log(null, "x", Duration.ZERO) shouldBe TimeError.EndNotAfterStart.left()
    }

    @Test
    fun `summarize clips to range, counts running and sorts`() {
        val entries = listOf(
            TimeEntry(1, 1, "", T0.minusSeconds(3600), T0.plusSeconds(1800)), // 1h30 but 30m in range
            TimeEntry(2, 2, "", T0.plusSeconds(3600), T0.plusSeconds(3600 + 7200)), // 2h
            TimeEntry(3, 1, "", T0.plusSeconds(10_000), null), // running, 100s until now
        )
        summarize(entries, from = T0, to = T0.plusSeconds(100_000), now = T0.plusSeconds(10_100)) shouldBe
            listOf(
                ProjectTotal(2, Duration.ofHours(2)),
                ProjectTotal(1, Duration.ofSeconds(1800 + 100)),
            )
    }

    @Test
    fun `duration formatting`() {
        Duration.ofSeconds(3725).formatHms() shouldBe "1:02:05"
        Duration.ofMinutes(125).formatShort() shouldBe "2h 05m"
        Duration.ofMinutes(45).formatShort() shouldBe "45m"
    }
}
