package com.zham.app.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.Note
import com.zham.domain.model.NoteRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.usecase.Edit
import com.zham.domain.usecase.LineKind
import com.zham.domain.usecase.continueList
import com.zham.domain.usecase.setLineKind
import com.zham.domain.usecase.toggleCheck
import com.zham.domain.usecase.toggleWrap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@Immutable
data class NoteEditorState(
    val title: String,
    val body: TextFieldValue,
    val categories: ImmutableList<PickerItem>,
    val projects: ImmutableList<PickerItem>,
    val categoryId: Long?,
    val projectId: Long?,
    val pinned: Boolean,
    /** True while the note is being read; a tap on a checkbox then ticks it. */
    val reading: Boolean,
    val existing: Boolean,
)

sealed interface NoteEditorEvent {
    data class SetTitle(val title: String) : NoteEditorEvent
    data class SetBody(val body: TextFieldValue) : NoteEditorEvent
    data object Bold : NoteEditorEvent
    data object Italic : NoteEditorEvent
    data class Colour(val color: Color) : NoteEditorEvent
    data class ListStyle(val kind: LineKind) : NoteEditorEvent
    data class SelectCategory(val id: Long?) : NoteEditorEvent
    data class SelectProject(val id: Long?) : NoteEditorEvent
    data object TogglePinned : NoteEditorEvent
    data class SetReading(val reading: Boolean) : NoteEditorEvent
    data class ToggleCheck(val line: Int) : NoteEditorEvent
    data object Save : NoteEditorEvent
    data object Delete : NoteEditorEvent
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ComposeViewModel<NoteEditorState, NoteEditorEvent>() {

    private var noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: 0L
    private var created = clock.instant()

    private val title = mutableStateOf("")
    private val body = mutableStateOf(TextFieldValue(""))
    private val categoryId = mutableStateOf<Long?>(null)
    private val projectId = mutableStateOf<Long?>(null)
    private val pinned = mutableStateOf(false)
    private val reading = mutableStateOf(noteId != 0L)
    private val closed = mutableStateOf(false)
    private var autosave: Job? = null

    val finished: Boolean get() = closed.value

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                noteRepository.findById(noteId)?.let { note ->
                    title.value = note.title
                    body.value = TextFieldValue(note.body)
                    categoryId.value = note.categoryId
                    projectId.value = note.projectId
                    pinned.value = note.pinned
                    created = note.created
                }
            }
        }
    }

    @Composable
    override fun uiState(): NoteEditorState {
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        return NoteEditorState(
            title = title.value,
            body = body.value,
            categories = categories.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            projects = projects
                .filter { !it.archived && (categoryId.value == null || it.categoryId == categoryId.value) }
                .map { PickerItem(it.id, it.name, Color(it.color), it.icon.ifBlank { null }) }
                .toImmutableList(),
            categoryId = categoryId.value,
            projectId = projectId.value,
            pinned = pinned.value,
            reading = reading.value,
            existing = noteId != 0L,
        )
    }

    override fun onEvent(event: NoteEditorEvent) {
        when (event) {
            is NoteEditorEvent.SetTitle -> {
                title.value = event.title
                scheduleSave()
            }
            is NoteEditorEvent.SetBody -> {
                val previous = body.value
                val continued = if (event.body.text != previous.text) {
                    continueList(previous.text, event.body.text, event.body.selection.start)
                } else {
                    null
                }
                body.value = continued?.let { TextFieldValue(it.text, TextRange(it.selStart, it.selEnd)) } ?: event.body
                scheduleSave()
            }
            NoteEditorEvent.Bold -> wrap("**")
            NoteEditorEvent.Italic -> wrap("*")
            is NoteEditorEvent.Colour -> {
                val hex = "%06X".format(event.color.toArgb() and 0xFFFFFF)
                wrap("[c=#$hex]", "[/c]")
            }
            is NoteEditorEvent.ListStyle -> {
                val value = body.value
                apply(setLineKind(value.text, value.selection.start, event.kind))
            }
            is NoteEditorEvent.SelectCategory -> {
                categoryId.value = event.id
                projectId.value = null
                scheduleSave()
            }
            is NoteEditorEvent.SelectProject -> {
                projectId.value = event.id
                scheduleSave()
            }
            NoteEditorEvent.TogglePinned -> {
                pinned.value = !pinned.value
                scheduleSave()
            }
            is NoteEditorEvent.SetReading -> reading.value = event.reading
            is NoteEditorEvent.ToggleCheck -> {
                body.value = TextFieldValue(toggleCheck(body.value.text, event.line))
                scheduleSave()
            }
            NoteEditorEvent.Save -> {
                autosave?.cancel()
                viewModelScope.launch { saveNow() }
            }
            NoteEditorEvent.Delete -> viewModelScope.launch {
                autosave?.cancel()
                if (noteId != 0L) noteRepository.delete(noteId)
                noteId = -1 // deleted: nothing may save it again
                closed.value = true
            }
        }
    }

    private fun wrap(open: String, close: String = open) {
        val value = body.value
        apply(toggleWrap(value.text, value.selection.start, value.selection.end, open, close))
    }

    private fun apply(edit: Edit) {
        body.value = TextFieldValue(edit.text, TextRange(edit.selStart, edit.selEnd))
        scheduleSave()
    }

    /** Saves a moment after the last change, so nothing is lost if the user just leaves. */
    private fun scheduleSave() {
        autosave?.cancel()
        autosave = viewModelScope.launch {
            delay(700)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        if (noteId < 0) return
        // An empty note is not worth keeping.
        if (title.value.isBlank() && body.value.text.isBlank()) return
        noteId = noteRepository.save(
            Note(
                id = noteId,
                title = title.value.trim(),
                body = body.value.text,
                categoryId = categoryId.value,
                projectId = projectId.value,
                created = created,
                updated = clock.instant(),
                pinned = pinned.value,
            )
        )
    }
}
