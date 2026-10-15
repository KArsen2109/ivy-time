package com.ivy.time.goals

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
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalRepository
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.SaveGoalUseCase
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.goalProgress
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.message
import com.ivy.time.ui.tickingNow
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
    val error: String?,
)

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
    ) : GoalsEvent

    data class Delete(val id: Long) : GoalsEvent
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val categoryRepository: CategoryRepository,
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
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val categoriesById = categories.associateBy { it.id }

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
                    },
                    progressLabel = progress.spent.formatShort() + " of " + goal.target.formatShort(),
                    fraction = progress.fraction,
                    met = progress.met,
                    color = Color(category?.color ?: DEFAULT_COLOR),
                    categoryId = goal.categoryId,
                    minutes = goal.target.toMinutes(),
                    period = goal.period,
                    type = goal.type,
                )
            }.toImmutableList(),
            categories = categories
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon) }
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
