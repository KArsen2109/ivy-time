package com.ivy.time.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.design.system.MINUTES_PER_DAY
import com.ivy.design.system.TimelineBlock
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.PlanOccurrence
import com.ivy.time.domain.model.PlanRepository
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.RepeatRule
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.model.occurrencesOn
import com.ivy.time.domain.usecase.CompletePlanUseCase
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import com.ivy.time.domain.usecase.dayPlan
import com.ivy.time.domain.usecase.minutesFromMidnight
import com.ivy.time.domain.usecase.formatHms
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.TimeFormatters
import com.ivy.time.ui.message
import com.ivy.time.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val UNCATEGORISED = 0xFF7A8CA8.toInt()

@HiltViewModel
class DayViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val planRepository: PlanRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val selectedDay: SelectedDay,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val completePlan: CompletePlanUseCase,
    private val clock: Clock,
) : ComposeViewModel<DayState, DayEvent>() {

    private val openPlanId = mutableStateOf<Long?>(null)
    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): DayState {
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val plans by remember { planRepository.observePlans() }.collectAsState(emptyList())
        val completions by remember { planRepository.observeCompletions() }
            .collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val date by remember { selectedDay.date }.collectAsState()
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)

        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val occurrences = occurrencesOn(plans, date, zone, completions.toSet())
        val day = dayPlan(date, occurrences, entries, zone, now)

        val plannedBlocks = occurrences.map { it.toBlock(date, zone, categoriesById) }
        val actualBlocks = day.entries.map { it.toBlock(date, zone, now, categoriesById, projectsById) }

        return DayState(
            date = date,
            dateLabel = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                today.plusDays(1) -> "Tomorrow"
                else -> formatters.shortDate(date)
            },
            weekdayLabel = formatters.weekdayAndDate(date),
            isToday = date == today,
            plannedLabel = day.planned.formatShort(),
            trackedLabel = day.tracked.formatShort(),
            keptPercent = (day.adherence * 100).toInt().coerceIn(0, 999),
            adherence = day.adherence,
            unplannedLabel = day.unplanned.formatShort(),
            planned = plannedBlocks.toImmutableList(),
            actual = actualBlocks.toImmutableList(),
            nowMinutes = if (date == today) {
                Duration.between(date.atStartOfDay(zone).toInstant(), now).toMinutes().toInt()
            } else {
                null
            },
            firstContentMinutes = (plannedBlocks + actualBlocks)
                .minOfOrNull { it.startMinutes }
                ?: 8 * 60,
            running = entries.firstOrNull { it.isRunning }?.let { entry ->
                val category = categoriesById[entry.categoryId]
                RunningUi(
                    elapsed = entry.duration(now).formatHms(),
                    title = entry.note.ifBlank { category?.name ?: "Tracking" },
                    since = "since " + formatters.time(entry.start),
                )
            },
            quickCategories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            sheet = openPlanId.value?.let { planId ->
                occurrences.firstOrNull { it.plan.id == planId }?.let { occurrence ->
                    val category = categoriesById[occurrence.plan.categoryId]
                    PlanSheet(
                        planId = planId,
                        title = occurrence.plan.note.ifBlank { category?.name ?: "Planned block" },
                        timeRange = formatters.time(occurrence.plan.start) + " – " +
                            formatters.time(occurrence.plan.end),
                        repeatLabel = occurrence.plan.repeat.label(),
                        done = occurrence.done,
                    )
                }
            },
            error = error.value,
        )
    }

    override fun onEvent(event: DayEvent) {
        when (event) {
            is DayEvent.ShiftDay -> selectedDay.shift(event.days)
            DayEvent.GoToToday -> selectedDay.set(LocalDate.now(clock))
            is DayEvent.QuickStart -> launchStart(event.categoryId, null, "")
            DayEvent.StopTimer -> viewModelScope.launch {
                stopTimer().fold({ error.value = it.message() }, { error.value = null })
            }
            is DayEvent.OpenPlan -> openPlanId.value = event.planId
            DayEvent.CloseSheet -> openPlanId.value = null
            is DayEvent.StartFromPlan -> viewModelScope.launch {
                val plan = planRepository.findById(event.planId)
                openPlanId.value = null
                if (plan != null) launchStart(plan.categoryId, plan.projectId, plan.note)
            }
            is DayEvent.TogglePlanDone -> viewModelScope.launch {
                val date = selectedDay.date.value
                openPlanId.value = null
                completePlan(event.planId, date)
                    .fold({ error.value = it.message() }, { error.value = null })
            }
            is DayEvent.DeletePlan -> viewModelScope.launch {
                openPlanId.value = null
                planRepository.delete(event.planId)
            }
            DayEvent.DismissError -> error.value = null
        }
    }

    private fun launchStart(categoryId: Long?, projectId: Long?, note: String) {
        viewModelScope.launch {
            startTimer(categoryId, projectId, note)
                .fold({ error.value = it.message() }, { error.value = null })
        }
    }

    private fun PlanOccurrence.toBlock(
        date: LocalDate,
        zone: ZoneId,
        categories: Map<Long, Category>,
    ): TimelineBlock {
        val category = categories[plan.categoryId]
        val from = minutesFromMidnight(startsAt, date, zone).coerceIn(0, MINUTES_PER_DAY - 10)
        val to = minutesFromMidnight(endsAt, date, zone)
        return TimelineBlock(
            id = plan.id,
            startMinutes = from,
            endMinutes = to.coerceIn(from + 10, MINUTES_PER_DAY),
            label = plan.note.ifBlank { category?.name ?: "Planned" },
            detail = plan.duration.formatShort(),
            color = Color(category?.color ?: UNCATEGORISED),
            done = done,
        )
    }

    private fun TimeEntry.toBlock(
        date: LocalDate,
        zone: ZoneId,
        now: Instant,
        categories: Map<Long, Category>,
        projects: Map<Long, Project>,
    ): TimelineBlock {
        val category = categories[categoryId]
        val from = minutesFromMidnight(start, date, zone).coerceIn(0, MINUTES_PER_DAY - 10)
        val to = minutesFromMidnight(end ?: now, date, zone)
        return TimelineBlock(
            id = id,
            startMinutes = from,
            endMinutes = to.coerceIn(from + 10, MINUTES_PER_DAY),
            label = note.ifBlank { category?.name ?: "Tracked" },
            detail = projects[projectId]?.name ?: duration(now).formatShort(),
            color = Color(category?.color ?: UNCATEGORISED),
            running = isRunning,
        )
    }
}

private fun RepeatRule.label(): String = when (this) {
    RepeatRule.Once -> "Once"
    RepeatRule.Daily -> "Every day"
    RepeatRule.Monthly -> "Every month"
    is RepeatRule.Weekly -> days.sorted().joinToString(", ") {
        it.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    }
}
