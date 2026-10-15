package com.zham.app.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.DateRange
import com.zham.domain.usecase.PeriodType
import com.zham.domain.usecase.dailyTotals
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.periodRange
import com.zham.domain.usecase.shift
import com.zham.domain.usecase.summarize
import com.zham.domain.usecase.summarizeByProject
import com.zham.domain.usecase.totalIn
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
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

/** The periods a report can cover. [Custom] is a range the user picked in the filter. */
enum class ReportPeriod { Day, Week, Month, Year, Custom }

@Immutable
data class ReportsState(
    val period: ReportPeriod,
    val rangeLabel: String,
    val total: String,
    val average: String,
    val slices: ImmutableList<ReportRow>,
    val projects: ImmutableList<ReportRow>,
    val bars: ImmutableList<Pair<String, Float>>,
    val barValues: ImmutableList<String>,
    val barCaption: String,
    val barPeak: String,
    val singleDay: Boolean,
    val records: ImmutableList<ReportRecord>,
    val categories: ImmutableList<PickerItem>,
    val selectedCategoryIds: Set<Long>,
    val customFrom: LocalDate?,
    val customTo: LocalDate?,
    val filterActive: Boolean,
    val canShift: Boolean,
    val today: LocalDate,
    val weekStart: DayOfWeek,
    val marks: CalendarMarks,
)

/** One slice of the split. [minutes] is what the donut and the legend share is drawn from. */
@Immutable
data class ReportRow(
    val name: String,
    val color: Color,
    val duration: String,
    val percent: String,
    val minutes: Float,
)

@Immutable
data class ReportRecord(
    val title: String,
    val path: String,
    val range: String,
    val description: String,
    val duration: String,
    val color: Color,
)

sealed interface ReportsEvent {
    data class SelectPeriod(val period: ReportPeriod) : ReportsEvent
    data class Shift(val steps: Long) : ReportsEvent
    data class ApplyFilter(
        val categoryIds: Set<Long>,
        val from: LocalDate?,
        val to: LocalDate?,
    ) : ReportsEvent
    data class RemoveCategory(val id: Long) : ReportsEvent
    data object ClearRange : ReportsEvent
    data object ClearFilter : ReportsEvent
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val planRepository: PlanRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ComposeViewModel<ReportsState, ReportsEvent>() {

    private val period = mutableStateOf(ReportPeriod.Week)
    private val offset = mutableLongStateOf(0L)
    private val categoryFilter = mutableStateOf<Set<Long>>(emptySet())
    private val customFrom = mutableStateOf<LocalDate?>(null)
    private val customTo = mutableStateOf<LocalDate?>(null)

    @Composable
    override fun uiState(): ReportsState {
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val allEntries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val plans by remember { planRepository.observePlans() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = allEntries.any { it.isRunning })

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)

        val range = rangeFor(today, weekStart)
        val from = range.startInstant(zone)
        val to = range.endInstant(zone)

        // The filter narrows what the numbers are made of, never just what is drawn.
        val chosen = categoryFilter.value
        val entries = if (chosen.isEmpty()) allEntries else allEntries.filter { it.categoryId in chosen }

        val total = totalIn(entries, from, to, now)
        val dayCount = (range.toExclusive.toEpochDay() - range.from.toEpochDay()).toInt().coerceAtLeast(1)
        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val days = dailyTotals(entries, range, zone, now)
        val single = dayCount == 1

        // A year of daily bars is unreadable, so long ranges are summed by month instead.
        val byMonth = dayCount > 92
        val bars: List<Pair<String, Float>>
        val barPeak: Duration
        if (byMonth) {
            val months = days.groupBy { java.time.YearMonth.from(it.first) }
                .map { (month, list) ->
                    month to list.fold(Duration.ZERO) { acc, it -> acc + it.second }
                }
            bars = months.map { (month, duration) ->
                month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
                    .take(1) to duration.toMinutes().toFloat()
            }
            barPeak = months.maxOfOrNull { it.second } ?: Duration.ZERO
        } else {
            bars = days.mapIndexed { index, (date, duration) ->
                barLabel(date, index, dayCount, formatters) to duration.toMinutes().toFloat()
            }
            barPeak = days.maxOfOrNull { it.second } ?: Duration.ZERO
        }

        return ReportsState(
            period = period.value,
            rangeLabel = if (single) {
                formatters.weekdayAndDate(range.from)
            } else {
                formatters.dayMonth(range.from) + " – " +
                    formatters.dayMonth(range.toExclusive.minusDays(1)) + " · " + dayCount + " days"
            },
            total = total.formatShort(),
            average = if (single) {
                "${summarize(entries, from, to, now).size} categories"
            } else {
                total.dividedBy(dayCount.toLong()).formatShort() + "/day"
            },
            slices = summarize(entries, from, to, now).map { row ->
                val category = categoriesById[row.categoryId]
                ReportRow(
                    name = category?.name ?: "No category",
                    color = Color(category?.color ?: UNCATEGORISED),
                    duration = row.total.formatShort(),
                    percent = percent(row.total, total),
                    minutes = row.total.toMinutes().toFloat(),
                )
            }.toImmutableList(),
            projects = summarizeByProject(entries, from, to, now)
                .filter { it.first != null }
                .map { (projectId, duration) ->
                    val project = projectsById[projectId]
                    val category = categoriesById[project?.categoryId]
                    ReportRow(
                        name = listOfNotNull(category?.name, project?.name).joinToString(" / "),
                        color = Color(project?.color ?: UNCATEGORISED),
                        duration = duration.formatShort(),
                        percent = percent(duration, total),
                        minutes = duration.toMinutes().toFloat(),
                    )
                }.toImmutableList(),
            bars = bars.toImmutableList(),
            // With room to spare each bar says its hours; a crowded chart keeps only its shape.
            barValues = bars.map { (_, minutes) ->
                if (bars.size <= 10 && minutes > 0f) "%.1fh".format(java.util.Locale.ENGLISH, minutes / 60f) else ""
            }.toImmutableList(),
            barCaption = if (byMonth) "One bar for each month" else "One bar for each day in this period",
            barPeak = "peak " + barPeak.formatShort(),
            singleDay = single,
            records = if (single) {
                dayRecords(entries, range, zone, now, categoriesById, projectsById, formatters)
            } else {
                emptyList<ReportRecord>().toImmutableList()
            },
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            selectedCategoryIds = chosen,
            customFrom = customFrom.value,
            customTo = customTo.value,
            filterActive = chosen.isNotEmpty() || period.value == ReportPeriod.Custom,
            canShift = period.value != ReportPeriod.Custom,
            today = today,
            weekStart = weekStart,
            marks = calendarMarks(allEntries, plans, zone),
        )
    }

    override fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.SelectPeriod -> {
                period.value = event.period
                offset.longValue = 0
                if (event.period == ReportPeriod.Custom && customFrom.value == null) {
                    val today = LocalDate.now(clock)
                    customFrom.value = today.minusDays(6)
                    customTo.value = today
                }
            }
            is ReportsEvent.Shift -> offset.longValue += event.steps
            is ReportsEvent.ApplyFilter -> {
                categoryFilter.value = event.categoryIds
                if (event.from != null && event.to != null) {
                    customFrom.value = minOf(event.from, event.to)
                    customTo.value = maxOf(event.from, event.to)
                    period.value = ReportPeriod.Custom
                }
            }
            is ReportsEvent.RemoveCategory -> categoryFilter.value = categoryFilter.value - event.id
            ReportsEvent.ClearRange -> {
                customFrom.value = null
                customTo.value = null
                period.value = ReportPeriod.Week
                offset.longValue = 0
            }
            ReportsEvent.ClearFilter -> {
                categoryFilter.value = emptySet()
                customFrom.value = null
                customTo.value = null
                if (period.value == ReportPeriod.Custom) period.value = ReportPeriod.Week
                offset.longValue = 0
            }
        }
    }

    private fun rangeFor(today: LocalDate, weekStart: DayOfWeek): DateRange {
        val custom = period.value == ReportPeriod.Custom
        val from = customFrom.value
        val to = customTo.value
        if (custom && from != null && to != null) return DateRange(from, to.plusDays(1))
        val type = when (period.value) {
            ReportPeriod.Day -> PeriodType.Day
            ReportPeriod.Week -> PeriodType.Week
            ReportPeriod.Month -> PeriodType.Month
            ReportPeriod.Year -> PeriodType.Year
            ReportPeriod.Custom -> PeriodType.Week
        }
        return periodRange(type, today, weekStart).shift(type, offset.longValue)
    }

    private fun barLabel(date: LocalDate, index: Int, dayCount: Int, formatters: TimeFormatters): String =
        when {
            dayCount <= 10 -> formatters.weekdayInitial(date)
            // Dense ranges label the date of every fifth bar, or the labels run into each other.
            index % 5 == 0 -> date.dayOfMonth.toString()
            else -> ""
        }

    private fun percent(part: Duration, total: Duration): String {
        if (total.isZero) return "0%"
        return "%d%%".format(part.toMillis() * 100 / total.toMillis())
    }

    private fun dayRecords(
        entries: List<TimeEntry>,
        range: DateRange,
        zone: java.time.ZoneId,
        now: java.time.Instant,
        categories: Map<Long, com.zham.domain.model.Category>,
        projects: Map<Long, com.zham.domain.model.Project>,
        formatters: TimeFormatters,
    ) = entries
        .filter { it.start < range.endInstant(zone) && (it.end ?: now) > range.startInstant(zone) }
        .sortedBy { it.start }
        .map { entry ->
            val category = categories[entry.categoryId]
            ReportRecord(
                title = entry.note.ifBlank { category?.name ?: "No category" },
                path = listOfNotNull(category?.name, projects[entry.projectId]?.name)
                    .joinToString(" / "),
                range = formatters.time(entry.start) + " – " +
                    (entry.end?.let(formatters::time) ?: "now"),
                description = entry.description,
                duration = entry.duration(now).formatShort(),
                color = Color(category?.color ?: UNCATEGORISED),
            )
        }
        .toImmutableList()
}
