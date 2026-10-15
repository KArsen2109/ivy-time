package com.ivy.time.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.PlanRepository
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.RepeatRule
import com.ivy.time.domain.usecase.SavePlanUseCase
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.TimeFormatters
import com.ivy.time.ui.message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class RepeatChoice { Once, Daily, Weekly, Monthly }

@Immutable
data class PlanEditorState(
    val editing: Boolean,
    val categories: ImmutableList<PickerItem>,
    val projects: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val dateLabel: String,
    val startLabel: String,
    val endLabel: String,
    val durationLabel: String,
    val crossesMidnight: Boolean,
    val repeat: RepeatChoice,
    val weekdays: Set<DayOfWeek>,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val use24Hours: Boolean,
    val error: String?,
)

sealed interface PlanEditorEvent {
    data class SelectCategory(val id: Long?) : PlanEditorEvent
    data class SelectProject(val id: Long?) : PlanEditorEvent
    data class SetNote(val note: String) : PlanEditorEvent
    data class SetDate(val date: LocalDate) : PlanEditorEvent
    data class SetStart(val time: LocalTime) : PlanEditorEvent
    data class SetEnd(val time: LocalTime) : PlanEditorEvent
    data class SetLength(val minutes: Long) : PlanEditorEvent
    data class SetRepeat(val repeat: RepeatChoice) : PlanEditorEvent
    data class ToggleWeekday(val day: DayOfWeek) : PlanEditorEvent
    data object Save : PlanEditorEvent
    data object Delete : PlanEditorEvent
}

@HiltViewModel
class PlanEditorViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val savePlan: SavePlanUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ComposeViewModel<PlanEditorState, PlanEditorEvent>() {

    private val planId: Long = savedStateHandle.get<String>("planId")?.toLongOrNull() ?: 0L
    private val startMinutes: Int = savedStateHandle.get<String>("minutes")?.toIntOrNull() ?: 540
    private val initialDate: LocalDate = savedStateHandle.get<String>("date")
        ?.toLongOrNull()
        ?.let(LocalDate::ofEpochDay)
        ?: LocalDate.now(clock)

    private val categoryId = mutableStateOf<Long?>(null)
    private val projectId = mutableStateOf<Long?>(null)
    private val note = mutableStateOf("")
    private val date = mutableStateOf(initialDate)
    private val start = mutableStateOf(LocalTime.ofSecondOfDay(startMinutes * 60L))
    private val end = mutableStateOf(LocalTime.ofSecondOfDay((startMinutes + 60) * 60L % 86400))
    private val repeat = mutableStateOf(RepeatChoice.Once)
    private val weekdays = mutableStateOf(setOf(initialDate.dayOfWeek))
    private val error = mutableStateOf<String?>(null)
    private val closed = mutableStateOf(false)

    val finished: Boolean get() = closed.value

    init {
        if (planId != 0L) {
            viewModelScope.launch {
                planRepository.findById(planId)?.let { plan ->
                    categoryId.value = plan.categoryId
                    projectId.value = plan.projectId
                    note.value = plan.note
                    date.value = plan.startDate
                    start.value = plan.start
                    end.value = plan.end
                    when (val rule = plan.repeat) {
                        RepeatRule.Once -> repeat.value = RepeatChoice.Once
                        RepeatRule.Daily -> repeat.value = RepeatChoice.Daily
                        RepeatRule.Monthly -> repeat.value = RepeatChoice.Monthly
                        is RepeatRule.Weekly -> {
                            repeat.value = RepeatChoice.Weekly
                            weekdays.value = rule.days
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun uiState(): PlanEditorState {
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val use24Hours = settings?.use24Hours ?: true
        val formatters = TimeFormatters(clock.zone, use24Hours)
        val selected = categoryId.value

        return PlanEditorState(
            editing = planId != 0L,
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            projects = projects
                .filter { !it.archived }
                .filter { selected == null || it.categoryId == null || it.categoryId == selected }
                .map { PickerItem(it.id, it.name, Color(it.color)) }
                .toImmutableList(),
            categoryId = selected,
            projectId = projectId.value,
            note = note.value,
            dateLabel = formatters.shortDate(date.value),
            startLabel = formatters.time(start.value),
            endLabel = formatters.time(end.value),
            durationLabel = length().formatShort(),
            crossesMidnight = !end.value.isAfter(start.value),
            repeat = repeat.value,
            weekdays = weekdays.value,
            date = date.value,
            start = start.value,
            end = end.value,
            use24Hours = use24Hours,
            error = error.value,
        )
    }

    override fun onEvent(event: PlanEditorEvent) {
        when (event) {
            is PlanEditorEvent.SelectCategory -> {
                categoryId.value = event.id
                projectId.value = null
            }
            is PlanEditorEvent.SelectProject -> projectId.value = event.id
            is PlanEditorEvent.SetNote -> note.value = event.note
            is PlanEditorEvent.SetDate -> {
                date.value = event.date
                if (repeat.value == RepeatChoice.Weekly) {
                    weekdays.value = weekdays.value.ifEmpty { setOf(event.date.dayOfWeek) }
                }
            }
            is PlanEditorEvent.SetStart -> {
                val shift = Duration.between(start.value, event.time)
                start.value = event.time
                end.value = end.value.plus(shift)
            }
            is PlanEditorEvent.SetEnd -> end.value = event.time
            is PlanEditorEvent.SetLength -> end.value = start.value.plusMinutes(event.minutes)
            is PlanEditorEvent.SetRepeat -> repeat.value = event.repeat
            is PlanEditorEvent.ToggleWeekday -> weekdays.value = weekdays.value.toMutableSet()
                .apply { if (!add(event.day)) remove(event.day) }
            PlanEditorEvent.Save -> save()
            PlanEditorEvent.Delete -> viewModelScope.launch {
                if (planId != 0L) planRepository.delete(planId)
                closed.value = true
            }
        }
    }

    private fun length(): Duration = Duration.between(start.value, end.value)
        .let { if (it.isNegative || it.isZero) it.plusDays(1) else it }

    private fun save() {
        viewModelScope.launch {
            savePlan(
                id = planId,
                categoryId = categoryId.value,
                projectId = projectId.value,
                note = note.value,
                start = start.value,
                end = end.value,
                startDate = date.value,
                repeat = when (repeat.value) {
                    RepeatChoice.Once -> RepeatRule.Once
                    RepeatChoice.Daily -> RepeatRule.Daily
                    RepeatChoice.Monthly -> RepeatRule.Monthly
                    RepeatChoice.Weekly -> RepeatRule.Weekly(weekdays.value)
                },
            ).fold(
                ifLeft = { error.value = it.message() },
                ifRight = {
                    error.value = null
                    closed.value = true
                },
            )
        }
    }
}
