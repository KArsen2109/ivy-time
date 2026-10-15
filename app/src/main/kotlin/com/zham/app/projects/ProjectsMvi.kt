package com.zham.app.projects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.SaveProjectUseCase
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.summarizeByProject
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.message
import com.zham.app.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Immutable
data class ProjectsState(
    val projects: ImmutableList<ProjectItem>,
    val categories: ImmutableList<PickerItem>,
    val error: String?,
)

@Immutable
data class ProjectItem(
    val id: Long,
    val name: String,
    val color: Color,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: Color?,
    val archived: Boolean,
    val total: String,
)

sealed interface ProjectsEvent {
    data class Save(
        val id: Long,
        val name: String,
        val categoryId: Long?,
        val color: Color,
        val archived: Boolean,
    ) : ProjectsEvent

    data class Delete(val id: Long) : ProjectsEvent
}

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val categoryRepository: CategoryRepository,
    private val timeRepository: TimeRepository,
    private val saveProject: SaveProjectUseCase,
    private val clock: Clock,
) : ComposeViewModel<ProjectsState, ProjectsEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): ProjectsState {
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val totals = summarizeByProject(entries, Instant.EPOCH, FAR_FUTURE, now).toMap()
        val categoryNames = categories.associate { it.id to it.name }
        val categoryColors = categories.associate { it.id to Color(it.color) }

        return ProjectsState(
            projects = projects.map {
                ProjectItem(
                    id = it.id,
                    name = it.name,
                    color = Color(it.color),
                    categoryId = it.categoryId,
                    categoryName = categoryNames[it.categoryId],
                    categoryColor = categoryColors[it.categoryId],
                    archived = it.archived,
                    total = (totals[it.id] ?: Duration.ZERO).formatShort(),
                )
            }.toImmutableList(),
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: ProjectsEvent) {
        when (event) {
            is ProjectsEvent.Save -> viewModelScope.launch {
                saveProject(
                    id = event.id,
                    name = event.name,
                    categoryId = event.categoryId,
                    color = event.color.toArgb(),
                    archived = event.archived,
                ).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is ProjectsEvent.Delete -> viewModelScope.launch { projectRepository.delete(event.id) }
        }
    }

    private companion object {
        val FAR_FUTURE: Instant = Instant.ofEpochMilli(Long.MAX_VALUE / 2)
    }
}
