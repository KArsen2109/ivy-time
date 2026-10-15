package com.ivy.time.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import com.ivy.time.data.SettingsStore
import com.ivy.time.data.ThemeMode
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.Csv
import com.ivy.time.ui.ComposeViewModel
import com.ivy.design.system.CategoryPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import javax.inject.Inject
import androidx.compose.ui.graphics.toArgb

@Immutable
data class SettingsState(
    val theme: ThemeMode,
    val weekStart: DayOfWeek,
    val use24Hours: Boolean,
    val entryCount: Int,
    val message: String?,
)

sealed interface SettingsEvent {
    data class SetTheme(val theme: ThemeMode) : SettingsEvent
    data class SetWeekStart(val day: DayOfWeek) : SettingsEvent
    data class SetUse24Hours(val use24Hours: Boolean) : SettingsEvent
    /** The screen hands over the file contents it read or the sink it opened. */
    data class ImportCsv(val content: String) : SettingsEvent
    data class ExportDone(val success: Boolean) : SettingsEvent
    data object DismissMessage : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
) : ComposeViewModel<SettingsState, SettingsEvent>() {

    private val message = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): SettingsState {
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())

        return SettingsState(
            theme = settings?.theme ?: ThemeMode.System,
            weekStart = settings?.weekStart ?: DayOfWeek.MONDAY,
            use24Hours = settings?.use24Hours ?: true,
            entryCount = entries.size,
            message = message.value,
        )
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetTheme -> viewModelScope.launch { settingsStore.setTheme(event.theme) }
            is SettingsEvent.SetWeekStart -> viewModelScope.launch {
                settingsStore.setWeekStart(event.day)
            }
            is SettingsEvent.SetUse24Hours -> viewModelScope.launch {
                settingsStore.setUse24Hours(event.use24Hours)
            }
            is SettingsEvent.ImportCsv -> importCsv(event.content)
            is SettingsEvent.ExportDone -> message.value =
                if (event.success) "Backup saved." else "Could not write the file."
            SettingsEvent.DismissMessage -> message.value = null
        }
    }

    /** Builds the CSV the screen then writes to the file the user picked. */
    suspend fun buildCsv(): String {
        val entries = timeRepository.observeEntries().first()
        val categories = categoryRepository.observeCategories().first().associateBy { it.id }
        val projects = projectRepository.observeProjects().first().associateBy { it.id }
        return Csv.export(entries, categories, projects)
    }

    private fun importCsv(content: String) {
        viewModelScope.launch {
            val rows = Csv.parse(content)
            if (rows.isEmpty()) {
                message.value = "No records found in that file."
                return@launch
            }
            // Categories are matched by name and created when missing, like Ivy Wallet's import.
            val categories = categoryRepository.observeCategories().first()
                .associateBy { it.name.lowercase() }
                .toMutableMap()
            val projects = projectRepository.observeProjects().first()
                .associateBy { it.name.lowercase() }

            rows.forEach { row ->
                val categoryId = row.category.takeIf { it.isNotBlank() }?.let { name ->
                    categories[name.lowercase()]?.id ?: run {
                        categoryRepository.save(
                            Category(
                                id = 0,
                                name = name,
                                color = CategoryPalette[categories.size % CategoryPalette.size].toArgb(),
                                icon = "other",
                                orderNum = categories.size.toDouble(),
                            )
                        )
                        val created = categoryRepository.observeCategories().first()
                            .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        created?.also { categories[name.lowercase()] = it }?.id
                    }
                }
                timeRepository.save(
                    TimeEntry(
                        id = 0,
                        categoryId = categoryId,
                        projectId = projects[row.project.lowercase()]?.id,
                        note = row.note,
                        start = row.start,
                        end = row.end ?: row.start.plusSeconds(60),
                    )
                )
            }
            message.value = "Imported ${rows.size} records."
        }
    }
}
