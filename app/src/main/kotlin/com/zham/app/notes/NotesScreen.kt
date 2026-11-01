package com.zham.app.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.PickerRow
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.ColorDot
import com.zham.design.system.EmptyNote
import com.zham.design.system.ScreenHeading
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme

@Composable
fun NotesScreen(onOpenNote: (Long) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: NotesViewModel = hiltViewModel()
    NotesUi(viewModel.uiState(), viewModel::onEvent, onOpenNote, modifier)
}

@Composable
fun NotesUi(
    state: NotesState,
    onEvent: (NotesEvent) -> Unit,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            ScreenHeading("Notes") {
                IconButton(onClick = { onOpenNote(0) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "New note")
                }
            }
            if (state.categories.isNotEmpty()) {
                PickerRow(
                    items = state.categories,
                    selectedId = state.categoryId,
                    onSelect = { onEvent(NotesEvent.SelectCategory(it)) },
                )
            }
            if (state.categoryId != null && state.projects.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PickerRow(
                    items = state.projects,
                    selectedId = state.projectId,
                    onSelect = { onEvent(NotesEvent.SelectProject(it)) },
                )
            }
            if (state.notes.isEmpty()) {
                EmptyNote(
                    if (state.anyNotes) {
                        "No notes in this category or project."
                    } else {
                        "Notes for projects and quick lists, like tonight's shopping. Tap + to write one."
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(state.notes, key = { it.id }) { note ->
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = { onOpenNote(note.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(note.color, size = 12.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (note.preview.isNotBlank()) {
                            Text(
                                note.preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhamTheme.extra.slate,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            listOf(note.where, note.updated).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    IconButton(onClick = { onEvent(NotesEvent.TogglePin(note.id)) }) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = if (note.pinned) "Unpin" else "Pin to the top",
                            tint = if (note.pinned) MaterialTheme.colorScheme.primary else ZhamTheme.extra.slate,
                        )
                    }
                    val askDelete = rememberDeleteConfirm("Delete this note?", "The note is removed.") {
                        onEvent(NotesEvent.Delete(note.id))
                    }
                    IconButton(onClick = askDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                    }
                }
            }
        }
    }
}
