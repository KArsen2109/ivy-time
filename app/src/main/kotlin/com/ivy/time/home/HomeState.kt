package com.ivy.time.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class HomeState(
    val todayTotal: String,
    val running: RunningUi?,
    val projects: ImmutableList<ProjectUi>,
    val days: ImmutableList<DayUi>,
    val error: HomeError?,
)

@Immutable
data class ProjectUi(val id: Long, val name: String, val color: Color)

@Immutable
data class RunningUi(val elapsed: String, val title: String, val color: Color?)

@Immutable
data class DayUi(val title: String, val total: String, val entries: ImmutableList<EntryUi>)

@Immutable
data class EntryUi(
    val id: Long,
    val title: String,
    val range: String,
    val duration: String,
    val color: Color?,
    val running: Boolean,
)

enum class HomeError { TimerAlreadyRunning, InvalidDuration }

sealed interface HomeEvent {
    data class Start(val projectId: Long?, val note: String) : HomeEvent
    data object Stop : HomeEvent
    data class Log(val projectId: Long?, val note: String, val minutes: Long) : HomeEvent
    data class Delete(val entryId: Long) : HomeEvent
    data object DismissError : HomeEvent
}
