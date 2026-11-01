package com.zham.app.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.app.ui.PickerRow
import com.zham.app.ui.rememberDeleteConfirm
import com.zham.design.system.SectionLabel
import com.zham.design.system.Spacing
import com.zham.design.system.ZhamTheme
import com.zham.domain.usecase.LineKind
import com.zham.domain.usecase.NoteLine
import com.zham.domain.usecase.parseNote

private val TEXT_COLOURS = listOf(
    Color(0xFFFF1744), Color(0xFFFF4081), Color(0xFFAA00FF), Color(0xFF2979FF),
    Color(0xFF00B0FF), Color(0xFF00C853), Color(0xFFFFC400), Color(0xFF889096),
)

@Composable
fun NoteEditorScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: NoteEditorViewModel = hiltViewModel()
    val state = viewModel.uiState()

    LaunchedEffect(viewModel.finished) {
        if (viewModel.finished) onClose()
    }
    // Leaving in any way (the arrow, the back gesture) saves first.
    DisposableEffect(Unit) {
        onDispose { viewModel.onEvent(NoteEditorEvent.Save) }
    }
    BackHandler {
        viewModel.onEvent(NoteEditorEvent.Save)
        onClose()
    }

    NoteEditorUi(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun NoteEditorUi(
    state: NoteEditorState,
    onEvent: (NoteEditorEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = {
                onEvent(NoteEditorEvent.Save)
                onClose()
            }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                if (state.existing) "Note" else "New note",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEvent(NoteEditorEvent.TogglePinned) }) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (state.pinned) "Unpin" else "Pin to the top",
                    tint = if (state.pinned) MaterialTheme.colorScheme.primary else ZhamTheme.extra.slate,
                )
            }
            IconButton(onClick = { onEvent(NoteEditorEvent.SetReading(!state.reading)) }) {
                Icon(
                    if (state.reading) Icons.Rounded.Edit else Icons.Rounded.MenuBook,
                    contentDescription = if (state.reading) "Edit" else "Read",
                )
            }
            if (state.existing) {
                val askDelete = rememberDeleteConfirm("Delete this note?", "The note is removed.") {
                    onEvent(NoteEditorEvent.Delete)
                }
                IconButton(onClick = askDelete) { Icon(Icons.Rounded.Delete, contentDescription = "Delete note") }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = { onEvent(NoteEditorEvent.SetTitle(it)) },
            label = { Text("Title") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 8.dp),
        )

        if (state.categories.isNotEmpty()) {
            SectionLabel("Category")
            PickerRow(
                items = state.categories,
                selectedId = state.categoryId,
                onSelect = { onEvent(NoteEditorEvent.SelectCategory(it)) },
            )
        }
        if (state.projects.isNotEmpty() && state.categoryId != null) {
            SectionLabel("Project")
            PickerRow(
                items = state.projects,
                selectedId = state.projectId,
                onSelect = { onEvent(NoteEditorEvent.SelectProject(it)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        if (state.reading) {
            NoteReader(state.body.text, onToggle = { onEvent(NoteEditorEvent.ToggleCheck(it)) })
        } else {
            Toolbar(onEvent)
            OutlinedTextField(
                value = state.body,
                onValueChange = { onEvent(NoteEditorEvent.SetBody(it)) },
                label = { Text("Write here") },
                minLines = 10,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )
            Text(
                "Lists continue when you press enter. Tap the book icon to read the note and tick its boxes.",
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter),
            )
        }
    }
}

@Composable
private fun Toolbar(onEvent: (NoteEditorEvent) -> Unit) {
    var colours by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { onEvent(NoteEditorEvent.Bold) }) { Icon(Icons.Rounded.FormatBold, "Bold") }
        IconButton(onClick = { onEvent(NoteEditorEvent.Italic) }) { Icon(Icons.Rounded.FormatItalic, "Italic") }
        Box {
            IconButton(onClick = { colours = true }) { Icon(Icons.Rounded.FormatColorText, "Text colour") }
            DropdownMenu(expanded = colours, onDismissRequest = { colours = false }) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TEXT_COLOURS.forEach { color ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    colours = false
                                    onEvent(NoteEditorEvent.Colour(color))
                                }
                        )
                    }
                }
            }
        }
        IconButton(onClick = { onEvent(NoteEditorEvent.ListStyle(LineKind.Bullet)) }) {
            Icon(Icons.Rounded.FormatListBulleted, "Bulleted list")
        }
        IconButton(onClick = { onEvent(NoteEditorEvent.ListStyle(LineKind.Numbered)) }) {
            Icon(Icons.Rounded.FormatListNumbered, "Numbered list")
        }
        IconButton(onClick = { onEvent(NoteEditorEvent.ListStyle(LineKind.Check)) }) {
            Icon(Icons.Rounded.Checklist, "Checklist")
        }
    }
}

@Composable
private fun NoteReader(body: String, onToggle: (Int) -> Unit) {
    val lines = remember(body) { parseNote(body) }
    Column(Modifier.padding(horizontal = Spacing.Gutter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line -> ReaderLine(line, onToggle) }
    }
}

@Composable
private fun ReaderLine(line: NoteLine, onToggle: (Int) -> Unit) {
    val text = styled(line)
    when (line.kind) {
        LineKind.Check -> Row(
            Modifier.fillMaxWidth().clickable { onToggle(line.sourceIndex) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (line.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = if (line.checked) "Done" else "Not done",
                tint = if (line.checked) MaterialTheme.colorScheme.primary else ZhamTheme.extra.slate,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (line.checked) ZhamTheme.extra.slate else MaterialTheme.colorScheme.onSurface,
            )
        }
        LineKind.Bullet -> Row { Text("•  ", style = MaterialTheme.typography.bodyLarge); Text(text, style = MaterialTheme.typography.bodyLarge) }
        LineKind.Numbered -> Row {
            Text("${line.number}.  ", style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
        LineKind.Plain -> Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun styled(line: NoteLine): AnnotatedString = buildAnnotatedString {
    line.spans.forEach { span ->
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                color = span.color?.let { Color(it) } ?: Color.Unspecified,
            )
        ) { append(span.text) }
    }
}
