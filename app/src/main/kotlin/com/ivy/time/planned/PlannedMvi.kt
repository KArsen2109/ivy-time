package com.ivy.time.planned

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.PlannedRepository
import com.ivy.time.domain.model.Recurrence
import com.ivy.time.domain.usecase.CompletePlannedUseCase
import com.ivy.time.domain.usecase.SavePlannedUseCase
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
data class PlannedState(
    val overdue: ImmutableList<PlannedItem>,
    val upcoming: ImmutableList<PlannedItem>,
    val overdueTotal: String,
    val upcomingTotal: String,
    val categories: ImmutableList<PickerItem>,
    val use24Hours: Boolean,
    val error: String?,
)

@Immutable
data class PlannedItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val duration: String,
    val color: Color,
    val icon: String?,
    val categoryId: Long?,
    val note: String,
    val minutes: Long,
    val date: LocalDate,
    val time: LocalTime,
    val recurrence: Recurrence,
)

sealed interface PlannedEvent {
    data class Save(
        val id: Long,
        val categoryId: Long?,
        val note: String,
        val minutes: Long,
        val date: LocalDate,
        val time: LocalTime,
        val recurrence: Recurrence,
    ) : PlannedEvent

    data class Complete(val id: Long) : PlannedEvent
    data class Delete(val id: Long) : PlannedEvent
}

@HiltViewModel
class PlannedViewModel @Inject constructor(
    private val plannedRepository: PlannedRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsStore: SettingsStore,
    private val savePlanned: SavePlannedUseCase,
    private val completePlanned: CompletePlannedUseCase,
    private val clock: Clock,
) : ComposeViewModel<PlannedState, PlannedEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): PlannedState {
        val planned by remember { plannedRepository.observePlanned() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)

        val use24Hours = settings?.use24Hours ?: true
        val formatters = TimeFormatters(clock.zone, use24Hours)
        val today = LocalDate.now(clock)
        val categoriesById = categories.associateBy { it.id }

        val items = planned.map { entry ->
            val category = categoriesById[entry.categoryId]
            PlannedItem(
                id = entry.id,
                title = entry.note.ifBlank { category?.name ?: "Planned time" },
                subtitle = listOfNotNull(
                    formatters.dayTitle(entry.date, today) + ", " + formatters.time(entry.time),
                    entry.recurrence.takeIf { it != Recurrence.OneTime }?.name,
                ).joinToString(" · "),
                duration = entry.duration.formatShort(),
                color = Color(category?.color ?: UNCATEGORIZED_COLOR),
                icon = category?.icon,
                categoryId = entry.categoryId,
                note = entry.note,
                minutes = entry.duration.toMinutes(),
                date = entry.date,
                time = entry.time,
                recurrence = entry.recurrence,
            )
        }
        val (overdue, upcoming) = items.partition { it.date.isBefore(today.plusDays(1)) }

        return PlannedState(
            overdue = overdue.toImmutableList(),
            upcoming = upcoming.toImmutableList(),
            overdueTotal = overdue.sumDuration().formatShort(),
            upcomingTotal = upcoming.sumDuration().formatShort(),
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            use24Hours = use24Hours,
            error = error.value,
        )
    }

    override fun onEvent(event: PlannedEvent) {
        when (event) {
            is PlannedEvent.Save -> viewModelScope.launch {
                savePlanned(
                    id = event.id,
                    categoryId = event.categoryId,
                    projectId = null,
                    note = event.note,
                    duration = Duration.ofMinutes(event.minutes),
                    date = event.date,
                    time = event.time,
                    recurrence = event.recurrence,
                ).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is PlannedEvent.Complete -> viewModelScope.launch {
                completePlanned(event.id).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is PlannedEvent.Delete -> viewModelScope.launch { plannedRepository.delete(event.id) }
        }
    }

    private fun List<PlannedItem>.sumDuration(): Duration =
        fold(Duration.ZERO) { acc, item -> acc + Duration.ofMinutes(item.minutes) }

    private companion object {
        const val UNCATEGORIZED_COLOR = 0xFF74747A.toInt()
    }
}
