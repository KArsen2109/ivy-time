package com.zham.app.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.tickingNow
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.StartTimerUseCase
import com.zham.domain.usecase.StopTimerUseCase
import com.zham.domain.usecase.formatHms
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@Immutable
data class ModeWheelState(
    val categories: ImmutableList<WheelItem>,
    /** Set once a category with projects has been tapped: the second ring shows its projects. */
    val openCategory: WheelItem?,
    val projects: ImmutableList<WheelItem>,
    val runningLabel: String?,
    val elapsed: String,
    val done: Boolean,
)

sealed interface ModeWheelEvent {
    data class PickCategory(val item: WheelItem) : ModeWheelEvent
    data class PickProject(val projectId: Long?) : ModeWheelEvent
    data object Back : ModeWheelEvent
    data object Stop : ModeWheelEvent
}

/**
 * The modes are the categories, and the projects under them. Picking one starts the timer at once;
 * if another is running it is stopped first, and so the time it ran is recorded as it really was.
 */
@HiltViewModel
class ModeWheelViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val startTimer: StartTimerUseCase,
    private val stopTimer: StopTimerUseCase,
    private val clock: Clock,
) : ComposeViewModel<ModeWheelState, ModeWheelEvent>() {

    private val open = mutableStateOf<WheelItem?>(null)
    private val done = mutableStateOf(false)

    @Composable
    override fun uiState(): ModeWheelState {
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val running = entries.firstOrNull { it.isRunning }
        val now by tickingNow(clock, active = running != null)
        val byId = categories.associateBy { it.id }

        return ModeWheelState(
            categories = categories.map { WheelItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            openCategory = open.value,
            projects = projects
                .filter { !it.archived && it.categoryId == open.value?.id }
                .map { WheelItem(it.id, it.name, Color(it.color), it.icon.ifBlank { null }) }
                .toImmutableList(),
            runningLabel = running?.let { it.note.ifBlank { byId[it.categoryId]?.name ?: "Tracking" } },
            elapsed = running?.duration(now)?.formatHms().orEmpty(),
            done = done.value,
        )
    }

    override fun onEvent(event: ModeWheelEvent) {
        when (event) {
            is ModeWheelEvent.PickCategory -> viewModelScope.launch {
                val hasProjects = projectRepository.observeProjects().let { flow ->
                    flow.first()
                }.any { !it.archived && it.categoryId == event.item.id }
                if (hasProjects) open.value = event.item else begin(event.item.id, null)
            }
            is ModeWheelEvent.PickProject -> viewModelScope.launch { begin(open.value?.id, event.projectId) }
            ModeWheelEvent.Back -> open.value = null
            ModeWheelEvent.Stop -> viewModelScope.launch {
                stopTimer()
                done.value = true
            }
        }
    }

    private suspend fun begin(categoryId: Long?, projectId: Long?) {
        // Switching modes ends the one running, so its time is recorded before the new one counts.
        if (timeRepository.findRunning() != null) stopTimer()
        startTimer(categoryId, projectId, "")
        done.value = true
    }
}
