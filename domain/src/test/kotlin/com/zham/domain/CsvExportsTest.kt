package com.zham.domain

import com.zham.domain.model.Category
import com.zham.domain.model.Note
import com.zham.domain.model.TimeEntry
import com.zham.domain.usecase.CsvExports
import com.zham.domain.usecase.Csv
import com.zham.domain.usecase.ExportPeriod
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test

class CsvExportsTest {
    private val zone = ZoneId.of("Asia/Yerevan") // UTC+4
    private val work = Category(1, "Աշխատանք", 0, "work", 0.0)
    private val entry = TimeEntry(1, 1, null, "Report, final", Instant.parse("2026-01-10T06:00:00Z"), Instant.parse("2026-01-10T08:30:00Z"), "d")
    private val now = Instant.parse("2026-01-11T00:00:00Z")

    @Test
    fun `records carry local columns and the first six columns still import`() {
        val csv = CsvExports.records(listOf(entry), mapOf(1L to work), emptyMap(), ExportPeriod(), zone, now)
        val rows = csv.lines()
        rows[0] shouldBe "start,end,category,project,note,description,date,from,to,minutes,moment"
        rows[1] shouldBe "2026-01-10T06:00:00Z,2026-01-10T08:30:00Z,Աշխատանք,,\"Report, final\",d,2026-01-10,10:00,12:30,150,"
        Csv.parse(csv).single().category shouldBe "Աշխատանք"
    }

    @Test
    fun `the period keeps only the days inside it`() {
        val other = entry.copy(id = 2, start = Instant.parse("2026-02-01T06:00:00Z"), end = Instant.parse("2026-02-01T07:00:00Z"))
        val csv = CsvExports.records(
            listOf(entry, other), mapOf(1L to work), emptyMap(),
            ExportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)), zone, now,
        )
        csv.lines().filter { it.isNotBlank() }.size shouldBe 2
    }

    @Test
    fun `text that would run as a formula is neutralised in the readable exports`() {
        val note = Note(1, "=SUM(A1)", "- milk", 1, null, now, now)
        val csv = CsvExports.notes(listOf(note), mapOf(1L to work), emptyMap(), ExportPeriod(), zone)
        csv.lines()[1].startsWith("'=SUM(A1),Աշխատանք") shouldBe true
        csv.contains("'- milk") shouldBe false // the body is plain text: a bullet, not a leading minus
        csv.contains("• milk") shouldBe true
    }
}
