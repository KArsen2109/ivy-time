package com.zham.app.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.StartTimerUseCase
import com.zham.domain.usecase.StopTimerUseCase
import com.zham.domain.usecase.formatHms
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.totalIn
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.TimeFormatters
import com.zham.app.ui.message
import com.zham.app.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

private const val UNCATEGORISED = 0xFF7A8CA8.toInt()

@Immutable
data class TimerState(
    val elapsed: String,
    val running: Boolean,
    /** 0f..1f of the current hour, for the ring. */
    val hourFraction: Float,
    val title: String,
    val subtitle: String,
    val color: Color,
    val note: String,
    val categories: ImmutableList<PickerItem>,
    val selectedCategoryId: Long?,
    val projects: ImmutableList<PickerItem>,
    val selectedProjectId: Long?,
    val categoryName: String,
    val todayLabel: String,
    val todayTotal: String,
    val records: ImmutableList<RecordRow>,
    val error: String?,
)

@Immutable
data class RecordRow(
    val id: Long,
    val title: String,
    val detail: String,
    val duration: String,
    val color: Color,
    val running: Boolean,
)

sealed interface TimerEvent {
    data class SelectCategory(val id: Long?) : TimerEvent
    data class SelectProject(val id: Long?) : TimerEvent
    data class SetNote(val note: String) : TimerEvent
    data object Toggle : TimerEvent
    data class Resume(val recordId: Long) : TimerEvent
    data class Delete(val recordId: Long) : TimerEvent
    data object DismissError : TimerEvent
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val clock: Clock,
) : ComposeViewModel<TimerState, TimerEvent>() {

    private val picked = mutableStateOf<Long?>(null)
    private val pickedProject = mutableStateOf<Long?>(null)
    private val note = mutableStateOf("")
    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): TimerState {
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val running = entries.firstOrNull { it.isRunning }
        val now by tickingNow(clock, active = running != null)

        val zone = clock.zone
        val today = LocalDate.now(clock)
        val formatters = TimeFormatters(zone, settings?.use24Hours ?: true)
        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }

        // The picker follows the running timer so the screen always shows what is being tracked.
        val selected = running?.categoryId ?: picked.value ?: categories.firstOrNull()?.id
        val runningCategory = categoriesById[running?.categoryId]
        val selectedCategory = categoriesById[selected]
        val elapsed = running?.duration(now) ?: Duration.ZERO

        // A record that crosses midnight belongs to both days, and only today's part is counted here.
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val todayEntries = entries.filter { it.start < dayEnd && (it.end ?: now) > dayStart }

        return TimerState(
            elapsed = elapsed.formatHms(),
            running = running != null,
            hourFraction = (elapsed.seconds % 3600) / 3600f,
            title = when {
                running != null -> running.note.ifBlank { runningCategory?.name ?: "Tracking" }
                else -> selectedCategory?.name ?: "Nothing selected"
            },
            subtitle = when {
                running != null -> listOfNotNull(
                    runningCategory?.name?.takeIf { running.note.isNotBlank() },
                    projectsById[running.projectId]?.name,
                    "since " + formatters.time(running.start),
                ).joinToString(" · ")
                selectedCategory != null -> listOfNotNull(
                    selectedCategory.name,
                    projectsById[pickedProject.value]?.name,
                ).joinToString(" / ") + " is selected"
                else -> "add a category first"
            },
            color = Color(
                (if (running != null) runningCategory else selectedCategory)?.color ?: UNCATEGORISED
            ),
            note = note.value,
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            selectedCategoryId = selected,
            // A project always sits under one category, so only that category's are offered.
            projects = projects
                .filter { !it.archived && it.categoryId == selected }
                .map { PickerItem(it.id, it.name, Color(it.color)) }
                .toImmutableList(),
            selectedProjectId = pickedProject.value?.takeIf { id ->
                projectsById[id]?.categoryId == selected
            },
            categoryName = (running?.let { runningCategory } ?: selectedCategory)?.name
                ?: "Nothing selected",
            todayLabel = formatters.weekdayAndDate(today),
            todayTotal = totalIn(todayEntries, dayStart, dayEnd, now).formatShort(),
            records = todayEntries.sortedByDescending { it.start }.map { entry ->
                val category = categoriesById[entry.categoryId]
                RecordRow(
                    id = entry.id,
                    title = entry.note.ifBlank { category?.name ?: "No category" },
                    detail = listOfNotNull(
                        listOfNotNull(category?.name, projectsById[entry.projectId]?.name)
                            .joinToString(" / ").takeIf { it.isNotBlank() },
                        formatters.time(entry.start) + " – " +
                            (entry.end?.let(formatters::time) ?: "now"),
                        entry.description.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    duration = entry.duration(now).formatShort(),
                    color = Color(category?.color ?: UNCATEGORISED),
                    running = entry.isRunning,
                )
            }.toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: TimerEvent) {
        when (event) {
            is TimerEvent.SelectCategory -> {
                picked.value = event.id
                pickedProject.value = null
            }
            is TimerEvent.SelectProject -> pickedProject.value = event.id
            is TimerEvent.SetNote -> note.value = event.note
            TimerEvent.Toggle -> viewModelScope.launch {
                val running = timeRepository.findRunning()
                val result = if (running != null) {
                    stopTimer()
                } else {
                    startTimer(
                        picked.value ?: categoryRepository.observeCategories().first().firstOrNull()?.id,
                        pickedProject.value,
                        note.value,
                    )
                        .also { note.value = "" }
                }
                result.fold({ error.value = it.message() }, { error.value = null })
            }
            is TimerEvent.Resume -> viewModelScope.launch {
                val source = timeRepository.findById(event.recordId) ?: return@launch
                picked.value = source.categoryId
                pickedProject.value = source.projectId
                startTimer(source.categoryId, source.projectId, source.note, source.description)
                    .fold({ error.value = it.message() }, { error.value = null })
            }
            is TimerEvent.Delete -> viewModelScope.launch {
                timeRepository.delete(event.recordId)
            }
            TimerEvent.DismissError -> error.value = null
        }
    }
}
