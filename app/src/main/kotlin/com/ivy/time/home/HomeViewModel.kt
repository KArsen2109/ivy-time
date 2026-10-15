package com.ivy.time.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.DateRange
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import com.ivy.time.domain.usecase.formatHms
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.periodRange
import com.ivy.time.domain.usecase.shift
import com.ivy.time.domain.usecase.totalIn
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.TimeFormatters
import com.ivy.time.ui.message
import com.ivy.time.ui.tickingNow
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val clock: Clock,
) : ComposeViewModel<HomeState, HomeEvent>() {

    private val error = mutableStateOf<String?>(null)
    private val search = mutableStateOf("")
    private val periodType = mutableStateOf(PeriodType.Month)
    private val periodOffset = mutableStateOf(0L)

    @Composable
    override fun uiState(): HomeState {
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)
        val range = periodRange(
            type = periodType.value,
            anchor = today,
            weekStart = settings?.weekStart ?: java.time.DayOfWeek.MONDAY,
        ).shift(periodType.value, periodOffset.value)

        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val query = search.value.trim()
        val visible = entries
            .filter { it.start < range.endInstant(zone) && (it.end ?: now) > range.startInstant(zone) }
            .filter { entry ->
                query.isEmpty() || entry.note.contains(query, ignoreCase = true) ||
                    categoriesById[entry.categoryId]?.name?.contains(query, true) == true ||
                    projectsById[entry.projectId]?.name?.contains(query, true) == true
            }

        return HomeState(
            periodLabel = periodLabel(range, today, formatters),
            periodType = periodType.value,
            periodTotal = totalIn(
                entries = entries,
                from = range.startInstant(zone),
                to = range.endInstant(zone),
                now = now,
            ).formatShort(),
            running = entries.firstOrNull { it.isRunning }?.let { entry ->
                val category = categoriesById[entry.categoryId]
                RunningUi(
                    elapsed = entry.duration(now).formatHms(),
                    title = entry.note.ifBlank { category?.name ?: "Tracking" },
                    subtitle = listOfNotNull(
                        category?.name?.takeIf { entry.note.isNotBlank() },
                        projectsById[entry.projectId]?.name,
                    ).joinToString(" · ").ifBlank { "Started " + formatters.time(entry.start) },
                )
            },
            quickCategories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            days = visible
                .groupBy { it.start.atZone(zone).toLocalDate() }
                .toList()
                .sortedByDescending { it.first }
                .map { (date, dayEntries) ->
                    dayUi(date, today, dayEntries, categoriesById, projectsById, now, formatters)
                }
                .toImmutableList(),
            search = search.value,
            error = error.value,
        )
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.QuickStart -> viewModelScope.launch {
                startTimer(event.categoryId, null, "").fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            HomeEvent.Stop -> viewModelScope.launch {
                stopTimer().fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is HomeEvent.Search -> search.value = event.query
            is HomeEvent.SelectPeriod -> {
                periodType.value = event.type
                periodOffset.value = 0
            }
            is HomeEvent.ShiftPeriod -> periodOffset.value += event.steps
            is HomeEvent.Delete -> viewModelScope.launch { timeRepository.delete(event.entryId) }
            HomeEvent.DismissError -> error.value = null
        }
    }

    private fun periodLabel(range: DateRange, today: LocalDate, formatters: TimeFormatters): String =
        when (periodType.value) {
            PeriodType.Day -> formatters.dayTitle(range.from, today)
            PeriodType.Week -> formatters.shortDate(range.from) + " – " +
                formatters.shortDate(range.toExclusive.minusDays(1))
            PeriodType.Month -> range.from.month.name.lowercase().replaceFirstChar { it.uppercase() } +
                " " + range.from.year
            PeriodType.Year -> range.from.year.toString()
        }

    private fun dayUi(
        date: LocalDate,
        today: LocalDate,
        entries: List<TimeEntry>,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
        now: Instant,
        formatters: TimeFormatters,
    ): DayUi {
        val total = entries.fold(Duration.ZERO) { acc, it -> acc + it.duration(now) }
        return DayUi(
            title = formatters.dayTitle(date, today),
            total = total.formatShort(),
            entries = entries.sortedByDescending { it.start }.map { entry ->
                val category = categories[entry.categoryId]
                val project = projects[entry.projectId]
                EntryUi(
                    id = entry.id,
                    title = entry.note.ifBlank { category?.name ?: "No category" },
                    subtitle = listOfNotNull(
                        category?.name?.takeIf { entry.note.isNotBlank() },
                        project?.name,
                    ).joinToString(" · "),
                    range = formatters.time(entry.start) + " – " +
                        (entry.end?.let(formatters::time) ?: "now"),
                    duration = entry.duration(now).formatShort(),
                    color = Color(category?.color ?: UNCATEGORIZED_COLOR),
                    icon = category?.icon,
                    running = entry.isRunning,
                )
            }.toImmutableList(),
        )
    }

    private companion object {
        const val UNCATEGORIZED_COLOR = 0xFF74747A.toInt()
    }
}
