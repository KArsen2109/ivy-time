package com.ivy.time.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.domain.usecase.dailyTotals
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.periodRange
import com.ivy.time.domain.usecase.shift
import com.ivy.time.domain.usecase.summarize
import com.ivy.time.domain.usecase.summarizeByProject
import com.ivy.time.domain.usecase.totalIn
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.TimeFormatters
import com.ivy.time.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class ReportsState(
    val periodType: PeriodType,
    val periodLabel: String,
    val total: String,
    val average: String,
    val slices: ImmutableList<ReportRow>,
    val projects: ImmutableList<ReportRow>,
    val bars: ImmutableList<Pair<String, Float>>,
)

@Immutable
data class ReportRow(
    val name: String,
    val color: Color,
    val duration: String,
    val percent: String,
    val minutes: Float,
)

sealed interface ReportsEvent {
    data class SelectPeriod(val type: PeriodType) : ReportsEvent
    data class Shift(val steps: Long) : ReportsEvent
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ComposeViewModel<ReportsState, ReportsEvent>() {

    private val periodType = mutableStateOf(PeriodType.Week)
    private val offset = mutableStateOf(0L)

    @Composable
    override fun uiState(): ReportsState {
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)
        val range = periodRange(periodType.value, today, weekStart).shift(periodType.value, offset.value)
        val from = range.startInstant(zone)
        val to = range.endInstant(zone)

        val total = totalIn(entries, from, to, now)
        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val days = dailyTotals(entries, range, zone, now)

        return ReportsState(
            periodType = periodType.value,
            periodLabel = when (periodType.value) {
                PeriodType.Day -> formatters.dayTitle(range.from, today)
                PeriodType.Week -> formatters.shortDate(range.from) + " – " +
                    formatters.shortDate(range.toExclusive.minusDays(1))
                PeriodType.Month ->
                    range.from.month.name.lowercase().replaceFirstChar { it.uppercase() } +
                        " " + range.from.year
                PeriodType.Year -> range.from.year.toString()
            },
            total = total.formatShort(),
            average = (total.dividedBy(days.size.coerceAtLeast(1).toLong())).formatShort(),
            slices = summarize(entries, from, to, now).map { row ->
                val category = categoriesById[row.categoryId]
                ReportRow(
                    name = category?.name ?: "No category",
                    color = Color(category?.color ?: UNCATEGORIZED_COLOR),
                    duration = row.total.formatShort(),
                    percent = percent(row.total, total),
                    minutes = row.total.toMinutes().toFloat(),
                )
            }.toImmutableList(),
            projects = summarizeByProject(entries, from, to, now)
                .filter { it.first != null }
                .map { (projectId, duration) ->
                    val project = projectsById[projectId]
                    ReportRow(
                        name = project?.name ?: "No project",
                        color = Color(project?.color ?: UNCATEGORIZED_COLOR),
                        duration = duration.formatShort(),
                        percent = percent(duration, total),
                        minutes = duration.toMinutes().toFloat(),
                    )
                }.toImmutableList(),
            bars = days
                .map { (date, duration) ->
                    barLabel(date, formatters) to duration.toMinutes().toFloat()
                }
                .let { bars -> if (bars.size > 31) bars.takeLast(31) else bars }
                .toImmutableList(),
        )
    }

    override fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.SelectPeriod -> {
                periodType.value = event.type
                offset.value = 0
            }
            is ReportsEvent.Shift -> offset.value += event.steps
        }
    }

    private fun barLabel(date: LocalDate, formatters: TimeFormatters): String =
        when (periodType.value) {
            PeriodType.Day, PeriodType.Week -> formatters.weekdayInitial(date)
            else -> date.dayOfMonth.toString()
        }

    private fun percent(part: Duration, total: Duration): String {
        if (total.isZero) return "0%"
        return "%d%%".format(part.toMillis() * 100 / total.toMillis())
    }

    private companion object {
        const val UNCATEGORIZED_COLOR = 0xFF74747A.toInt()
    }
}
