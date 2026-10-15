package com.ivy.time.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TimeFormatters(private val zone: ZoneId, use24Hours: Boolean) {
    private val time = DateTimeFormatter.ofPattern(if (use24Hours) "HH:mm" else "h:mm a")
    private val full = DateTimeFormatter.ofPattern("EEEE d MMMM")
    private val short = DateTimeFormatter.ofPattern("EEE d MMM")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")

    fun time(instant: Instant): String = time.withZone(zone).format(instant)

    fun time(value: LocalTime): String = time.format(value)

    fun weekdayAndDate(date: LocalDate): String = full.format(date)

    fun shortDate(date: LocalDate): String = short.format(date)

    fun dayMonth(date: LocalDate): String = dayMonth.format(date)

    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> short.format(date)
    }

    fun weekdayInitial(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
}
