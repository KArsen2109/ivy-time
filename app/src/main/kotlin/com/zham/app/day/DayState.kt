package com.zham.app.day

import androidx.compose.runtime.Immutable
import com.zham.design.system.TimelineBlock
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.PickerItem
import kotlinx.collections.immutable.ImmutableList
import java.time.DayOfWeek
import java.time.LocalDate

@Immutable
data class DayState(
    val date: LocalDate,
    val dateLabel: String,
    val weekdayLabel: String,
    val isToday: Boolean,
    val plannedLabel: String,
    val trackedLabel: String,
    val keptPercent: Int,
    val adherence: Float,
    val unplannedLabel: String,
    /** Time still to come in the day, and the part of it no planned block has claimed. */
    val leftLabel: String,
    val freeLabel: String,
    val planned: ImmutableList<TimelineBlock>,
    val actual: ImmutableList<TimelineBlock>,
    val nowMinutes: Int?,
    val firstContentMinutes: Int,
    val running: RunningUi?,
    val quickCategories: ImmutableList<PickerItem>,
    val sheet: PlanSheet?,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val marks: CalendarMarks,
    val error: String?,
)

@Immutable
data class RunningUi(val elapsed: String, val title: String, val since: String)

@Immutable
data class PlanSheet(
    val planId: Long,
    val title: String,
    val timeRange: String,
    val repeatLabel: String,
    val description: String,
    val done: Boolean,
    /** The day this occurrence belongs to; yesterday's for the tail of a block past midnight. */
    val date: LocalDate,
    val point: Boolean,
    /** True when the block has not begun yet, so ticking it off means it was done early. */
    val startsInFuture: Boolean,
    val plannedMinutes: Long,
    val now: java.time.LocalTime,
    val is24Hour: Boolean,
)

sealed interface DayEvent {
    data class ShiftDay(val days: Long) : DayEvent
    data class SetDay(val date: LocalDate) : DayEvent
    data object GoToToday : DayEvent
    data class QuickStart(val categoryId: Long) : DayEvent
    data object StopTimer : DayEvent
    data class OpenPlan(val planId: Long) : DayEvent
    data object CloseSheet : DayEvent
    data class StartFromPlan(val planId: Long) : DayEvent
    /** Ticks off a block that was done ahead of time, with the times it was really done. */
    data class CompletePlanEarly(
        val planId: Long,
        val date: LocalDate,
        val start: java.time.LocalTime,
        val end: java.time.LocalTime,
    ) : DayEvent
    data class TogglePlanDone(val planId: Long, val date: LocalDate, val done: Boolean) : DayEvent
    data class DeletePlan(val planId: Long) : DayEvent
    data object DismissError : DayEvent
}
