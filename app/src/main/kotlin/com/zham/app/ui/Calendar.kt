package com.zham.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zham.design.system.ZhamShapes
import com.zham.design.system.ZhamTheme
import com.zham.design.system.MonthCalendar
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.Spacing
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.occursOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Which days have something on them, so the calendar can dot them. */
@Immutable
class CalendarMarks(
    private val entryDates: Set<LocalDate>,
    private val plans: List<PlanBlock>,
) {
    fun has(date: LocalDate): Boolean = date in entryDates || plans.any { it.occursOn(date) }
}

fun calendarMarks(
    entries: List<TimeEntry>,
    plans: List<PlanBlock>,
    zone: ZoneId,
): CalendarMarks = CalendarMarks(
    entryDates = entries.map { it.start.atZone(zone).toLocalDate() }.toSet(),
    plans = plans,
)

/** Any date, across all the history, one tap from the screen that needs it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    title: String,
    selected: LocalDate,
    today: LocalDate,
    marks: CalendarMarks,
    weekStart: DayOfWeek,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZhamShapes.Sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter)
                .padding(bottom = 28.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            MonthCalendar(
                month = month,
                selected = selected,
                today = today,
                marked = marks::has,
                weekStart = weekStart,
                onPick = onPick,
                onMonthChange = { month = it },
            )
            Text(
                "A dot marks a day that has something on it.",
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                QuietButton("Close", onDismiss, Modifier.weight(1f))
                PrimaryButton("Today", { onPick(today) }, Modifier.weight(1f))
            }
        }
    }
}
