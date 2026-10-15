package com.ivy.time.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeError
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.LogTimeUseCase
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import com.ivy.time.domain.usecase.formatHms
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.startInstant
import com.ivy.time.domain.usecase.summarize
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val projectRepository: ProjectRepository,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val logTime: LogTimeUseCase,
    private val clock: Clock,
) : ComposeViewModel<HomeState, HomeEvent>() {

    private val error = mutableStateOf<HomeError?>(null)

    @Composable
    override fun uiState(): HomeState {
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val byId = projects.associateBy { it.id }
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val todayTotal = summarize(
            entries = entries,
            from = today.startInstant(zone),
            to = today.plusDays(1).startInstant(zone),
            now = now,
        ).fold(Duration.ZERO) { acc, it -> acc + it.total }

        return HomeState(
            todayTotal = todayTotal.formatShort(),
            running = entries.firstOrNull { it.isRunning }?.let { entry ->
                val project = byId[entry.projectId]
                RunningUi(
                    elapsed = entry.duration(now).formatHms(),
                    title = entry.note.ifBlank { project?.name ?: "Tracking" },
                    color = project?.let { Color(it.color) },
                )
            },
            projects = projects.map { ProjectUi(it.id, it.name, Color(it.color)) }.toImmutableList(),
            days = entries
                .groupBy { it.start.atZone(zone).toLocalDate() }
                .map { (date, dayEntries) -> dayUi(date, today, dayEntries, byId, now) }
                .toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.Start -> launchUseCase { startTimer(event.projectId, event.note) }
            HomeEvent.Stop -> launchUseCase { stopTimer() }
            is HomeEvent.Log -> launchUseCase {
                logTime(event.projectId, event.note, Duration.ofMinutes(event.minutes))
            }
            is HomeEvent.Delete -> viewModelScope.launch { timeRepository.delete(event.entryId) }
            HomeEvent.DismissError -> error.value = null
        }
    }

    private fun launchUseCase(block: suspend () -> arrow.core.Either<TimeError, Unit>) {
        viewModelScope.launch {
            block().fold(
                ifLeft = { error.value = it.toHomeError() },
                ifRight = { error.value = null },
            )
        }
    }

    private fun TimeError.toHomeError(): HomeError? = when (this) {
        TimeError.TimerAlreadyRunning -> HomeError.TimerAlreadyRunning
        TimeError.EndNotAfterStart -> HomeError.InvalidDuration
        TimeError.NoRunningTimer, TimeError.BlankName -> null
    }

    private fun dayUi(
        date: LocalDate,
        today: LocalDate,
        entries: List<TimeEntry>,
        projects: Map<Long, Project>,
        now: Instant,
    ): DayUi {
        val zone = clock.zone
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val total = entries.fold(Duration.ZERO) { acc, it -> acc + it.duration(now) }
        return DayUi(
            title = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
            },
            total = total.formatShort(),
            entries = entries.map { entry ->
                val project = projects[entry.projectId]
                EntryUi(
                    id = entry.id,
                    title = entry.note.ifBlank { project?.name ?: "No project" },
                    range = time.format(entry.start) + " – " + (entry.end?.let(time::format) ?: "now"),
                    duration = entry.duration(now).formatShort(),
                    color = project?.let { Color(it.color) },
                    running = entry.isRunning,
                )
            }.toImmutableList(),
        )
    }
}
