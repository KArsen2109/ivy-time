package com.zham.app.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.zham.design.system.CategoryPalette
import com.zham.data.SettingsStore
import com.zham.data.ThemeMode
import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.BuildBackupUseCase
import com.zham.domain.usecase.Csv
import com.zham.domain.usecase.FullBackup
import com.zham.domain.usecase.RestoreBackupUseCase
import com.zham.domain.usecase.goalProgress
import com.zham.app.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class MoreState(
    val categoryCount: Int,
    val categoryColors: ImmutableList<Color>,
    val projectCount: Int,
    val projectColors: ImmutableList<Color>,
    val goalCount: Int,
    val goalsMet: Int,
    val goalProgress: Float,
    val theme: ThemeMode,
    val weekStart: DayOfWeek,
    val use24Hours: Boolean,
    val recordCount: Int,
    val message: String?,
)

sealed interface MoreEvent {
    data class SetTheme(val theme: ThemeMode) : MoreEvent
    data class SetWeekStart(val day: DayOfWeek) : MoreEvent
    data class SetUse24Hours(val use24Hours: Boolean) : MoreEvent
    data class ImportCsv(val content: String) : MoreEvent
    data class ExportDone(val success: Boolean) : MoreEvent
    data object DismissMessage : MoreEvent
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val goalRepository: GoalRepository,
    private val buildBackup: BuildBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val clock: Clock,
) : ComposeViewModel<MoreState, MoreEvent>() {

    private val message = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): MoreState {
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val goals by remember { goalRepository.observeGoals() }.collectAsState(emptyList())

        val today = LocalDate.now(clock)
        val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
        val progress = goals.map {
            goalProgress(it, entries, today, clock.zone, clock.instant(), weekStart)
        }

        return MoreState(
            categoryCount = categories.size,
            categoryColors = categories.take(6).map { Color(it.color) }.toImmutableList(),
            projectCount = projects.size,
            projectColors = projects.take(6).map { Color(it.color) }.toImmutableList(),
            goalCount = goals.size,
            goalsMet = progress.count { it.met },
            goalProgress = if (goals.isEmpty()) {
                0f
            } else {
                progress.count { it.met }.toFloat() / goals.size
            },
            theme = settings?.theme ?: ThemeMode.System,
            weekStart = weekStart,
            use24Hours = settings?.use24Hours ?: true,
            recordCount = entries.size,
            message = message.value,
        )
    }

    override fun onEvent(event: MoreEvent) {
        when (event) {
            is MoreEvent.SetTheme -> viewModelScope.launch { settingsStore.setTheme(event.theme) }
            is MoreEvent.SetWeekStart -> viewModelScope.launch {
                settingsStore.setWeekStart(event.day)
            }
            is MoreEvent.SetUse24Hours -> viewModelScope.launch {
                settingsStore.setUse24Hours(event.use24Hours)
            }
            is MoreEvent.ImportCsv -> importCsv(event.content)
            is MoreEvent.ExportDone -> message.value =
                if (event.success) "Backup saved." else "Could not write the file."
            MoreEvent.DismissMessage -> message.value = null
        }
    }

    /** Builds the CSV the screen then writes to the file the user picked. */
    suspend fun buildCsv(): String {
        val entries = timeRepository.observeEntries().first()
        val categories = categoryRepository.observeCategories().first().associateBy { it.id }
        val projects = projectRepository.observeProjects().first().associateBy { it.id }
        return Csv.export(entries, categories, projects)
    }

    /** Everything the app holds, as the text of the full backup file. */
    suspend fun buildFullBackup(): String = FullBackup.encode(buildBackup())

    /** Accepts a full backup or a records CSV; both are merged without duplicating anything. */
    private fun importCsv(content: String) {
        viewModelScope.launch {
            val data = FullBackup.decode(content)
                ?: FullBackup.fromCsv(content).takeIf { !it.isEmpty }
            if (data == null) {
                message.value = "That file is not a Zham backup or a records CSV."
                return@launch
            }
            val result = restoreBackup(data) { index ->
                CategoryPalette[index % CategoryPalette.size].toArgb()
            }
            message.value = buildString {
                append("Added ${result.entries} records")
                if (result.plans > 0) append(", ${result.plans} blocks")
                if (result.goals > 0) append(", ${result.goals} goals")
                append(".")
                if (result.skipped > 0) append(" ${result.skipped} were already there.")
            }
        }
    }
}
