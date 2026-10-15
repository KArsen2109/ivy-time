package com.ivy.time.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TimeFormatters(private val zone: ZoneId, use24Hours: Boolean) {
    private val timePattern = if (use24Hours) "HH:mm" else "h:mm a"
    private val time = DateTimeFormatter.ofPattern(timePattern)
    private val dateFull = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val dateShort = DateTimeFormatter.ofPattern("d MMM")

    fun time(instant: Instant): String = time.withZone(zone).format(instant)

    fun time(value: LocalTime): String = time.format(value)

    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> dateFull.format(date)
    }

    fun date(date: LocalDate): String = dateFull.format(date)

    fun shortDate(date: LocalDate): String = dateShort.format(date)

    fun weekdayInitial(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
}
