package com.zham.app.goals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.GoalType
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.SaveGoalUseCase
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.goalProgress
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.message
import com.zham.app.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class GoalsState(
    val goals: ImmutableList<GoalItem>,
    val categories: ImmutableList<PickerItem>,
    val projects: ImmutableList<GoalProject>,
    val error: String?,
)

/** A project a goal can follow; [categoryId] is the category it sits under. */
@Immutable
data class GoalProject(val id: Long, val name: String, val color: Color, val categoryId: Long?)

@Immutable
data class GoalItem(
    val id: Long,
    val name: String,
    val subtitle: String,
    val progressLabel: String,
    val fraction: Float,
    val met: Boolean,
    val color: Color,
    val categoryId: Long?,
    val projectId: Long?,
    val matchTitle: Boolean,
    val minutes: Long,
    val period: GoalPeriod,
    val type: GoalType,
)

sealed interface GoalsEvent {
    data class Save(
        val id: Long,
        val name: String,
        val categoryId: Long?,
        val minutes: Long,
        val period: GoalPeriod,
        val type: GoalType,
        val projectId: Long?,
        val matchTitle: Boolean,
    ) : GoalsEvent

    data class Delete(val id: Long) : GoalsEvent
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val settingsStore: SettingsStore,
    private val saveGoal: SaveGoalUseCase,
    private val clock: Clock,
) : ComposeViewModel<GoalsState, GoalsEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): GoalsState {
        val goals by remember { goalRepository.observeGoals() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }

        return GoalsState(
            goals = goals.map { goal ->
                val progress = goalProgress(goal, entries, today, clock.zone, now, weekStart)
                val category = categoriesById[goal.categoryId]
                GoalItem(
                    id = goal.id,
                    name = goal.name,
                    subtitle = buildString {
                        append(if (goal.type == GoalType.AtLeast) "At least " else "At most ")
                        append(goal.target.formatShort())
                        append(if (goal.period == GoalPeriod.Week) " per week" else " per month")
                        append(" · ")
                        append(category?.name ?: "All categories")
                        projectsById[goal.projectId]?.let { append(" / ").append(it.name) }
                        if (goal.matchTitle) append(" · titled \"").append(goal.name).append("\"")
                    },
                    progressLabel = progress.spent.formatShort() + " of " + goal.target.formatShort(),
                    fraction = progress.fraction,
                    met = progress.met,
                    color = Color(category?.color ?: DEFAULT_COLOR),
                    categoryId = goal.categoryId,
                    projectId = goal.projectId,
                    matchTitle = goal.matchTitle,
                    minutes = goal.target.toMinutes(),
                    period = goal.period,
                    type = goal.type,
                )
            }.toImmutableList(),
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
                .toImmutableList(),
            projects = projects
                .filter { !it.archived }
                .map { GoalProject(it.id, it.name, Color(it.color), it.categoryId) }
                .toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: GoalsEvent) {
        when (event) {
            is GoalsEvent.Save -> viewModelScope.launch {
                saveGoal(
                    id = event.id,
                    name = event.name,
                    categoryId = event.categoryId,
                    target = Duration.ofMinutes(event.minutes),
                    period = event.period,
                    type = event.type,
                    projectId = event.projectId,
                    matchTitle = event.matchTitle,
                ).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is GoalsEvent.Delete -> viewModelScope.launch { goalRepository.delete(event.id) }
        }
    }

    private companion object {
        const val DEFAULT_COLOR = 0xFF6B4DFF.toInt()
    }
}
