package com.zham.app.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.zham.design.system.TimelineBlock
import com.zham.design.system.WeekDayColumn
import com.zham.data.SettingsStore
import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.GoalType
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.model.occurrencesOn
import com.zham.domain.usecase.PeriodType
import com.zham.domain.usecase.dayPlan
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.goalProgress
import com.zham.domain.usecase.goalRecords
import com.zham.domain.usecase.minutesFromMidnight
import com.zham.domain.usecase.periodRange
import com.zham.domain.usecase.shift
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.TimeFormatters
import com.zham.app.ui.calendarMarks
import com.zham.app.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

private const val UNCATEGORISED = 0xFF7A8CA8.toInt()

@Immutable
data class WeekState(
    val rangeLabel: String,
    val plannedLabel: String,
    val trackedLabel: String,
    val keptPercent: Int,
    val busiestLabel: String,
    val days: ImmutableList<WeekDayColumn>,
    val dates: ImmutableList<LocalDate>,
    val goals: ImmutableList<GoalRow>,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val marks: CalendarMarks,
)

@Immutable
data class GoalRow(
    val id: Long,
    val name: String,
    val detail: String,
    val fraction: Float,
    val met: Boolean,
    val color: Color,
    val recordCount: Int,
    val periodLabel: String,
    val days: ImmutableList<GoalDay>,
)

/** One day of a goal's records, so the total on the card can be traced to its parts. */
@Immutable
data class GoalDay(val title: String, val total: String, val records: ImmutableList<GoalRecordRow>)

@Immutable
data class GoalRecordRow(
    val title: String,
    val path: String,
    val range: String,
    val description: String,
    val duration: String,
    val color: Color,
)

sealed interface WeekEvent {
    data class ShiftWeek(val weeks: Long) : WeekEvent
}

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val planRepository: PlanRepository,
    private val categoryRepository: CategoryRepository,
    private val goalRepository: GoalRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ComposeViewModel<WeekState, WeekEvent>() {

    private val weekOffset = mutableLongStateOf(0L)

    @Composable
    override fun uiState(): WeekState {
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val plans by remember { planRepository.observePlans() }.collectAsState(emptyList())
        val completions by remember { planRepository.observeCompletions() }
            .collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val goals by remember { goalRepository.observeGoals() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)
        val range = periodRange(PeriodType.Week, today, weekStart)
            .shift(PeriodType.Week, weekOffset.longValue)
        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val completed = completions.toSet()

        val dates = (0..6).map { range.from.plusDays(it.toLong()) }
        val plansByDay = dates.map { date ->
            val occurrences = occurrencesOn(plans, date, zone, completed)
            date to dayPlan(date, occurrences, entries, zone, now)
        }

        val planned = plansByDay.fold(Duration.ZERO) { acc, (_, day) -> acc + day.planned }
        val tracked = plansByDay.fold(Duration.ZERO) { acc, (_, day) -> acc + day.tracked }
        val kept = plansByDay.fold(Duration.ZERO) { acc, (_, day) -> acc + day.kept }
        val busiest = plansByDay.maxByOrNull { it.second.tracked }

        return WeekState(
            rangeLabel = formatters.dayMonth(range.from) + " – " +
                formatters.dayMonth(range.toExclusive.minusDays(1)),
            plannedLabel = planned.formatShort(),
            trackedLabel = tracked.formatShort(),
            keptPercent = if (planned.isZero) {
                0
            } else {
                (kept.toMillis() * 100 / planned.toMillis()).toInt()
            },
            busiestLabel = busiest
                ?.takeIf { !it.second.tracked.isZero }
                ?.let { formatters.weekdayInitial(it.first) + " " + it.second.tracked.formatShort() }
                ?: "nothing tracked",
            days = plansByDay.map { (date, day) ->
                WeekDayColumn(
                    label = formatters.weekdayInitial(date),
                    dayNumber = date.dayOfMonth.toString(),
                    planned = day.occurrences.map { occurrence ->
                        val category = categoriesById[occurrence.plan.categoryId]
                        TimelineBlock(
                            id = occurrence.plan.id,
                            startMinutes = minutesFromMidnight(occurrence.startsAt, date, zone),
                            endMinutes = minutesFromMidnight(occurrence.endsAt, date, zone),
                            label = "",
                            detail = "",
                            color = Color(category?.color ?: UNCATEGORISED),
                        )
                    },
                    actual = day.entries.map { entry ->
                        val category = categoriesById[entry.categoryId]
                        TimelineBlock(
                            id = entry.id,
                            startMinutes = minutesFromMidnight(entry.start, date, zone),
                            endMinutes = minutesFromMidnight(entry.end ?: now, date, zone),
                            label = "",
                            detail = "",
                            color = Color(category?.color ?: UNCATEGORISED),
                        )
                    },
                    isToday = date == today,
                )
            }.toImmutableList(),
            dates = dates.toImmutableList(),
            goals = goals.map { goal ->
                val progress = goalProgress(goal, entries, range.from, zone, now, weekStart)
                val category: Category? = categoriesById[goal.categoryId]
                val records = goalRecords(goal, entries, range.from, zone, now, weekStart)
                GoalRow(
                    id = goal.id,
                    name = goal.name,
                    detail = progress.spent.formatShort() + " of " +
                        (if (goal.type == GoalType.AtLeast) "at least " else "at most ") +
                        goal.target.formatShort(),
                    fraction = progress.fraction,
                    met = progress.met,
                    color = Color(category?.color ?: UNCATEGORISED),
                    recordCount = records.size,
                    periodLabel = if (goal.period == GoalPeriod.Week) "this week" else "this month",
                    days = records
                        .groupBy { it.date }
                        .toList()
                        .sortedByDescending { it.first }
                        .map { (date, dayRecords) ->
                            GoalDay(
                                title = formatters.shortDate(date),
                                total = dayRecords
                                    .fold(Duration.ZERO) { acc, it -> acc + it.counted }
                                    .formatShort(),
                                records = dayRecords.map { record ->
                                    val entry = record.entry
                                    val entryCategory = categoriesById[entry.categoryId]
                                    GoalRecordRow(
                                        title = entry.note.ifBlank { entryCategory?.name ?: "No category" },
                                        path = listOfNotNull(
                                            entryCategory?.name,
                                            projectsById[entry.projectId]?.name,
                                        ).joinToString(" / "),
                                        range = formatters.time(entry.start) + " – " +
                                            (entry.end?.let(formatters::time) ?: "now"),
                                        description = entry.description,
                                        duration = record.counted.formatShort(),
                                        color = Color(entryCategory?.color ?: UNCATEGORISED),
                                    )
                                }.toImmutableList(),
                            )
                        }
                        .toImmutableList(),
                )
            }.toImmutableList(),
            today = today,
            weekStart = weekStart,
            marks = remember(entries, plans, zone) { calendarMarks(entries, plans, zone) },
        )
    }

    override fun onEvent(event: WeekEvent) {
        when (event) {
            is WeekEvent.ShiftWeek -> weekOffset.longValue += event.weeks
        }
    }
}
