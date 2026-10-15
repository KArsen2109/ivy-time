package com.zham.app.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.SaveEntryUseCase
import com.zham.domain.usecase.formatShort
import com.zham.app.ui.CalendarMarks
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.TimeFormatters
import com.zham.app.ui.calendarMarks
import com.zham.app.ui.message
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

@Immutable
data class EntryState(
    val editing: Boolean,
    val categories: ImmutableList<PickerItem>,
    val projects: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val description: String,
    val dateLabel: String,
    val startLabel: String,
    val endLabel: String,
    val durationLabel: String,
    val crossesMidnight: Boolean,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val use24Hours: Boolean,
    val today: LocalDate,
    val now: LocalTime,
    val weekStart: DayOfWeek,
    val marks: CalendarMarks,
    val error: String?,
    val canSave: Boolean,
    /** True when another record already covers part of this time. */
    val overlapsOther: Boolean,
)

sealed interface EntryEvent {
    data class SelectCategory(val id: Long?) : EntryEvent
    data class SelectProject(val id: Long?) : EntryEvent
    data class SetNote(val note: String) : EntryEvent
    data class SetDescription(val description: String) : EntryEvent
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
    private val planRepository: PlanRepository,
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
    private val description = mutableStateOf("")
    private val date = mutableStateOf(initialDate)
    // Within the first hour of the day "an hour ago" would be yesterday evening, so start at midnight.
    private val start = mutableStateOf(
        LocalTime.now(clock).withSecond(0).withNano(0).let { now ->
            if (now.isBefore(LocalTime.of(1, 0))) LocalTime.MIDNIGHT else now.minusHours(1)
        }
    )
    /**
     * The exact length of a record that was loaded, kept until the user changes a time. The times
     * on screen are whole minutes and cannot say how long a record of seconds, or one across a
     * clock change, really was.
     */
    private val fixedDuration = mutableStateOf<Duration?>(null)
    private var saving = false
    /** The exact start of a loaded record, used again while its date and start time stay as loaded. */
    private var loadedStart: Triple<LocalDate, LocalTime, java.time.Instant>? = null
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
                    // A running timer has no end to edit; it is stopped from the timer instead.
                    if (entry.end == null) {
                        closed.value = true
                        return@let
                    }
                    val zone = clock.zone
                    val startLocal = entry.start.atZone(zone)
                    val endLocal = (entry.end ?: entry.start).atZone(zone)
                    categoryId.value = entry.categoryId
                    projectId.value = entry.projectId
                    note.value = entry.note
                    description.value = entry.description
                    date.value = startLocal.toLocalDate()
                    start.value = startLocal.toLocalTime().withSecond(0).withNano(0)
                    end.value = endLocal.toLocalTime().withSecond(0).withNano(0)
                    fixedDuration.value = Duration.between(entry.start, entry.end)
                    loadedStart = Triple(date.value, start.value, entry.start)
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
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val plans by remember { planRepository.observePlans() }.collectAsState(emptyList())
        val use24Hours = settings?.use24Hours ?: true
        val formatters = TimeFormatters(clock.zone, use24Hours)

        val selectedCategory = categoryId.value
        // Worked out from what is on screen now, so it goes away as soon as the input is fixed.
        val problem = if (endsInTheFuture()) "This record would end after the current time." else null
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
            description = description.value,
            dateLabel = formatters.shortDate(date.value),
            startLabel = formatters.time(start.value),
            endLabel = formatters.time(end.value),
            durationLabel = duration().formatShort(),
            crossesMidnight = fixedDuration.value == null && !end.value.isAfter(start.value),
            date = date.value,
            start = start.value,
            end = end.value,
            use24Hours = use24Hours,
            today = LocalDate.now(clock),
            now = LocalTime.now(clock).withSecond(0).withNano(0),
            weekStart = settings?.weekStart ?: DayOfWeek.MONDAY,
            marks = remember(entries, plans, clock.zone) { calendarMarks(entries, plans, clock.zone) },
            error = problem ?: error.value,
            canSave = problem == null,
            overlapsOther = overlapsOther(entries),
        )
    }

    override fun onEvent(event: EntryEvent) {
        // A message from an earlier save describes input that may no longer exist.
        if (event != EntryEvent.Save) error.value = null
        when (event) {
            is EntryEvent.SelectCategory -> {
                categoryId.value = event.id
                projectId.value = null
            }
            is EntryEvent.SelectProject -> projectId.value = event.id
            is EntryEvent.SetNote -> note.value = event.note
            is EntryEvent.SetDescription -> description.value = event.description
            is EntryEvent.SetDate -> date.value = event.date
            is EntryEvent.SetStart -> {
                fixedDuration.value = null
                start.value = event.time
            }
            is EntryEvent.SetEnd -> {
                fixedDuration.value = null
                end.value = event.time
            }
            is EntryEvent.SetDuration -> {
                fixedDuration.value = null
                // Counting back past midnight puts the start on the day before.
                val endMinute = end.value.hour * 60 + end.value.minute
                if (event.minutes > endMinute) date.value = date.value.minusDays(1)
                start.value = end.value.minusMinutes(event.minutes)
            }
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
        return fixedDuration.value ?: if (raw.isNegative || raw.isZero) raw.plusDays(1) else raw
    }

    private fun overlapsOther(entries: List<com.zham.domain.model.TimeEntry>): Boolean {
        val from = date.value.atTime(start.value).atZone(clock.zone).toInstant()
        val to = from.plus(duration())
        val now = clock.instant()
        return entries.any { it.id != entryId && it.start < to && (it.end ?: now) > from }
    }

    private fun endsInTheFuture(): Boolean {
        val startInstant = date.value.atTime(start.value).atZone(clock.zone).toInstant()
        return startInstant.plus(duration()).isAfter(clock.instant())
    }

    private fun save() {
        if (saving || endsInTheFuture()) return
        saving = true
        viewModelScope.launch {
            val zone = clock.zone
            val startInstant = loadedStart
                ?.takeIf { it.first == date.value && it.second == start.value }?.third
                ?: date.value.atTime(start.value).atZone(zone).toInstant()
            val endInstant = startInstant.plus(duration())
            saveEntry(
                id = entryId,
                categoryId = categoryId.value,
                projectId = projectId.value,
                note = note.value,
                start = startInstant,
                end = endInstant,
                description = description.value,
            ).fold(
                ifLeft = {
                    saving = false
                    error.value = it.message()
                },
                ifRight = {
                    error.value = null
                    closed.value = true
                },
            )
        }
    }
}
