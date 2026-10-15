package com.ivy.time.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.SaveProjectUseCase
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.summarize
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Immutable
data class ProjectsState(val projects: ImmutableList<ProjectItem>)

@Immutable
data class ProjectItem(val id: Long, val name: String, val color: Color, val total: String)

sealed interface ProjectsEvent {
    data class Add(val name: String, val color: Color) : ProjectsEvent
    data class Delete(val id: Long) : ProjectsEvent
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val saveProject: SaveProjectUseCase,
    private val clock: Clock,
) : ComposeViewModel<ProjectsState, ProjectsEvent>() {

    @Composable
    override fun uiState(): ProjectsState {
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val totals = summarize(entries, Instant.EPOCH, Instant.MAX, now).associate { it.projectId to it.total }
        return ProjectsState(
            projects = projects.map {
                ProjectItem(it.id, it.name, Color(it.color), (totals[it.id] ?: Duration.ZERO).formatShort())
            }.toImmutableList()
        )
    }

    override fun onEvent(event: ProjectsEvent) {
        viewModelScope.launch {
            when (event) {
                is ProjectsEvent.Add -> saveProject(event.name, event.color.toArgb())
                is ProjectsEvent.Delete -> projectRepository.delete(event.id)
            }
        }
    }
}
