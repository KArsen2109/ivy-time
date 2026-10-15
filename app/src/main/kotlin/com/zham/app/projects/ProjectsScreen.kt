package com.zham.app.projects

import com.zham.app.ui.rememberDeleteConfirm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.CategoryPalette
import com.zham.design.system.ColorDot
import com.zham.design.system.EmptyNote
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamTheme
import com.zham.design.system.ScreenHeading
import com.zham.design.system.Spacing
import com.zham.app.ui.ColorPickerRow
import com.zham.app.ui.PickerItem
import com.zham.app.ui.PickerRow

@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) {
    val viewModel: ProjectsViewModel = hiltViewModel()
    ProjectsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun ProjectsUi(
    state: ProjectsState,
    onEvent: (ProjectsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var edited by remember { mutableStateOf<ProjectItem?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("Projects") {
                IconButton(
                    onClick = {
                        edited = null
                        dialogVisible = true
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New project")
                }
            }
            if (state.projects.isEmpty()) {
                EmptyNote(
                    "A project is a longer effort inside a category, like Work and a client site. " +
                        "Projects are optional."
                )
            }
        }
        val uncategorized = state.projects.filter { it.categoryId == null }
        if (uncategorized.isNotEmpty()) {
            item(key = "group-none") {
                GroupHeader("Needs a category", ZhamTheme.extra.slate, "Open one and pick where it belongs.")
            }
            items(uncategorized, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onClick = {
                        edited = project
                        dialogVisible = true
                    },
                    onDelete = { onEvent(ProjectsEvent.Delete(project.id)) },
                )
            }
        }
        // Projects sit under the category they belong to, so the layout is the hierarchy.
        state.categories.forEach { category ->
            val inCategory = state.projects.filter { it.categoryId == category.id }
            if (inCategory.isEmpty()) return@forEach
            item(key = "group-" + category.id) {
                GroupHeader(category.name, category.color, null)
            }
            items(inCategory, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onClick = {
                        edited = project
                        dialogVisible = true
                    },
                    onDelete = { onEvent(ProjectsEvent.Delete(project.id)) },
                )
            }
        }
    }

    if (dialogVisible) {
        ProjectDialog(
            initial = edited,
            categories = state.categories,
            onDismiss = { dialogVisible = false },
            onSave = { id, name, categoryId, color, archived ->
                onEvent(ProjectsEvent.Save(id, name, categoryId, color, archived))
                dialogVisible = false
            },
        )
    }
}

@Composable
private fun ProjectDialog(
    initial: ProjectItem?,
    categories: List<PickerItem>,
    onDismiss: () -> Unit,
    onSave: (Long, String, Long?, Color, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: CategoryPalette.first()) }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var archived by remember { mutableStateOf(initial?.archived ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New project" else "Edit project") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                if (categories.isNotEmpty()) {
                    PickerRow(
                        items = categories,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )
                }
                ColorPickerRow(CategoryPalette, color, onSelect = { color = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Archived", Modifier.weight(1f))
                    Switch(checked = archived, onCheckedChange = { archived = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                // A project sits under exactly one category, so one has to be chosen first.
                enabled = name.isNotBlank() && (categories.isEmpty() || categoryId != null),
                onClick = { onSave(initial?.id ?: 0L, name, categoryId, color, archived) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GroupHeader(name: String, color: Color, hint: String?) {
    Column(Modifier.padding(horizontal = Spacing.Gutter).padding(top = 22.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, size = 10.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = ZhamTheme.extra.slate,
            )
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = ZhamTheme.extra.slate)
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectItem, onClick: () -> Unit, onDelete: () -> Unit) {
    ZhamCard(
        Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(project.color, size = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (project.archived) project.name + " (archived)" else project.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    project.total + " tracked",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhamTheme.extra.slate,
                )
            }
            val askDelete = rememberDeleteConfirm(
                "Delete this project?", "Records and blocks keep their time but lose the project. Goals set on it are deleted.", onDelete,
            )
            IconButton(onClick = askDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete project")
            }
        }
    }
}
