package com.ivy.time.entry

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
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.SaveEntryUseCase
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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@Immutable
data class EntryState(
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
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val use24Hours: Boolean,
    val error: String?,
)

sealed interface EntryEvent {
    data class SelectCategory(val id: Long?) : EntryEvent
    data class SelectProject(val id: Long?) : EntryEvent
    data class SetNote(val note: String) : EntryEvent
    data class SetDate(val date: LocalDate) : EntryEvent
    data class SetStart(val time: LocalTime) : EntryEvent
    data class SetEnd(val time: LocalTime) : EntryEvent
    /** Moves the start back from the end by a fixed amount, for one-tap logging. */
    data class SetDuration(val minutes: Long) : EntryEvent
    data object Save : EntryEvent
    data object Delete : EntryEvent
}

@HiltViewModel
class EntryViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val saveEntry: SaveEntryUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ComposeViewModel<EntryState, EntryEvent>() {

    private val entryId: Long = savedStateHandle.get<String>("entryId")?.toLongOrNull() ?: 0L

    /** A new record opened from a day defaults to that day rather than to today. */
    private val initialDate: LocalDate = savedStateHandle.get<String>("date")
        ?.toLongOrNull()
        ?.takeIf { it >= 0 }
        ?.let(LocalDate::ofEpochDay)
        ?: LocalDate.now(clock)

    private val categoryId = mutableStateOf<Long?>(null)
    private val projectId = mutableStateOf<Long?>(null)
    private val note = mutableStateOf("")
    private val date = mutableStateOf(initialDate)
    private val start = mutableStateOf(LocalTime.now(clock).withSecond(0).withNano(0).minusHours(1))
    private val end = mutableStateOf(LocalTime.now(clock).withSecond(0).withNano(0))
    private val error = mutableStateOf<String?>(null)
    private val loaded = mutableStateOf(entryId == 0L)
    private val closed = mutableStateOf(false)

    /** Set once the screen may be popped. */
    val finished: Boolean get() = closed.value

    init {
        if (entryId != 0L) {
            viewModelScope.launch {
                timeRepository.findById(entryId)?.let { entry ->
                    val zone = clock.zone
                    val startLocal = entry.start.atZone(zone)
                    val endLocal = (entry.end ?: entry.start).atZone(zone)
                    categoryId.value = entry.categoryId
                    projectId.value = entry.projectId
                    note.value = entry.note
                    date.value = startLocal.toLocalDate()
                    start.value = startLocal.toLocalTime().withSecond(0).withNano(0)
                    end.value = endLocal.toLocalTime().withSecond(0).withNano(0)
                }
                loaded.value = true
            }
        }
    }

    @Composable
    override fun uiState(): EntryState {
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val use24Hours = settings?.use24Hours ?: true
        val formatters = TimeFormatters(clock.zone, use24Hours)

        val selectedCategory = categoryId.value
        return EntryState(
            editing = entryId != 0L,
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            projects = projects
                .filter { !it.archived }
                .filter { selectedCategory == null || it.categoryId == null || it.categoryId == selectedCategory }
                .map { PickerItem(it.id, it.name, Color(it.color)) }
                .toImmutableList(),
            categoryId = selectedCategory,
            projectId = projectId.value,
            note = note.value,
            dateLabel = formatters.date(date.value),
            startLabel = formatters.time(start.value),
            endLabel = formatters.time(end.value),
            durationLabel = duration().formatShort(),
            crossesMidnight = !end.value.isAfter(start.value),
            date = date.value,
            start = start.value,
            end = end.value,
            use24Hours = use24Hours,
            error = error.value,
        )
    }

    override fun onEvent(event: EntryEvent) {
        when (event) {
            is EntryEvent.SelectCategory -> {
                categoryId.value = event.id
                projectId.value = null
            }
            is EntryEvent.SelectProject -> projectId.value = event.id
            is EntryEvent.SetNote -> note.value = event.note
            is EntryEvent.SetDate -> date.value = event.date
            is EntryEvent.SetStart -> start.value = event.time
            is EntryEvent.SetEnd -> end.value = event.time
            is EntryEvent.SetDuration -> start.value = end.value.minusMinutes(event.minutes)
            EntryEvent.Save -> save()
            EntryEvent.Delete -> viewModelScope.launch {
                if (entryId != 0L) timeRepository.delete(entryId)
                closed.value = true
            }
        }
    }

    /** An end that is not after the start means the record ran past midnight. */
    private fun duration(): Duration {
        val raw = Duration.between(start.value, end.value)
        return if (raw.isNegative || raw.isZero) raw.plusDays(1) else raw
    }

    private fun save() {
        viewModelScope.launch {
            val zone = clock.zone
            val startInstant = date.value.atTime(start.value).atZone(zone).toInstant()
            val endInstant = startInstant.plus(duration())
            saveEntry(
                id = entryId,
                categoryId = categoryId.value,
                projectId = projectId.value,
                note = note.value,
                start = startInstant,
                end = endInstant,
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
