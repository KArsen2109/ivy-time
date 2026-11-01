package com.zham.app.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.TimeFormatters
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.NoteRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.usecase.plainText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@Immutable
data class NoteRow(
    val id: Long,
    val title: String,
    val preview: String,
    val where: String,
    val updated: String,
    val pinned: Boolean,
    val color: Color,
)

@Immutable
data class NotesState(
    val notes: ImmutableList<NoteRow>,
    val categories: ImmutableList<PickerItem>,
    val projects: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val projectId: Long?,
    val anyNotes: Boolean,
)

sealed interface NotesEvent {
    data class SelectCategory(val id: Long?) : NotesEvent
    data class SelectProject(val id: Long?) : NotesEvent
    data class TogglePin(val id: Long) : NotesEvent
    data class Delete(val id: Long) : NotesEvent
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) : ComposeViewModel<NotesState, NotesEvent>() {

    private val categoryId = mutableStateOf<Long?>(null)
    private val projectId = mutableStateOf<Long?>(null)

    @Composable
    override fun uiState(): NotesState {
        val notes by remember { noteRepository.observeNotes() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val settings by remember { settingsStore.settings }.collectAsState(null)
        val formatters = TimeFormatters(clock.zone, settings?.use24Hours ?: true)

        val categoriesById = categories.associateBy { it.id }
        val projectsById = projects.associateBy { it.id }
        val shown = notes.filter { note ->
            (categoryId.value == null || note.categoryId == categoryId.value) &&
                (projectId.value == null || note.projectId == projectId.value)
        }
        return NotesState(
            notes = shown.map { note ->
                val category = categoriesById[note.categoryId]
                NoteRow(
                    id = note.id,
                    title = note.title.ifBlank { "Untitled" },
                    preview = plainText(note.body).lineSequence().filter { it.isNotBlank() }.take(2).joinToString("\n"),
                    where = listOfNotNull(category?.name, projectsById[note.projectId]?.name).joinToString(" / "),
                    updated = formatters.shortDate(note.updated.atZone(clock.zone).toLocalDate()),
                    pinned = note.pinned,
                    color = Color(category?.color ?: 0xFF7A8CA8.toInt()),
                )
            }.toImmutableList(),
            categories = categories.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            projects = projects
                .filter { !it.archived && (categoryId.value == null || it.categoryId == categoryId.value) }
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon.ifBlank { null }) }
                .toImmutableList(),
            categoryId = categoryId.value,
            projectId = projectId.value,
            anyNotes = notes.isNotEmpty(),
        )
    }

    override fun onEvent(event: NotesEvent) {
        when (event) {
            is NotesEvent.SelectCategory -> {
                categoryId.value = event.id
                projectId.value = null
            }
            is NotesEvent.SelectProject -> projectId.value = event.id
            is NotesEvent.TogglePin -> viewModelScope.launch {
                noteRepository.findById(event.id)?.let { noteRepository.save(it.copy(pinned = !it.pinned)) }
            }
            is NotesEvent.Delete -> viewModelScope.launch { noteRepository.delete(event.id) }
        }
    }
}
