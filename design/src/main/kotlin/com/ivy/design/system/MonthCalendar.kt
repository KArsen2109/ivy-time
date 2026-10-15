package com.ivy.design.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * One month as a grid. A dot under a day means something is recorded or planned on it, so the
 * days worth opening can be found without opening each one. Always English, on purpose.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    marked: (LocalDate) -> Boolean,
    weekStart: DayOfWeek,
    onPick: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
            }
        }

        val weekdays = (0..6).map { weekStart.plus(it.toLong()) }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(1),
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = IvyTheme.extra.slate,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // The first column is the first weekday the user chose, not always Monday.
        val lead = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date -> DayCell(date, selected, today, marked, onPick, Modifier.weight(1f)) }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
            Box(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: LocalDate,
    today: LocalDate,
    marked: (LocalDate) -> Boolean,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    if (date == null) {
        Box(modifier)
        return
    }
    val isSelected = date == selected
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSelected) primary else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (date == today && !isSelected) {
                    Modifier.border(1.dp, primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onPick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
        if (marked(date)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else primary)
            )
        }
    }
}
