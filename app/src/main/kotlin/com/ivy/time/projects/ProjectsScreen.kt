package com.ivy.time.projects

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
import com.ivy.design.system.CategoryPalette
import com.ivy.design.system.ColorDot
import com.ivy.design.system.EmptyNote
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.ScreenHeading
import com.ivy.design.system.Spacing
import com.ivy.time.ui.ColorPickerRow
import com.ivy.time.ui.PickerItem
import com.ivy.time.ui.PickerRow

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
                    "A project is a longer effort inside a category, like Work and Ivy Time. " +
                        "Projects are optional."
                )
            }
        }
        items(state.projects, key = { it.id }) { project ->
            IvyCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = {
                    edited = project
                    dialogVisible = true
                },
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
                            listOfNotNull(project.categoryName, project.total + " tracked")
                                .joinToString(" and "),
                            style = MaterialTheme.typography.bodySmall,
                            color = IvyTheme.extra.slate,
                        )
                    }
                    IconButton(onClick = { onEvent(ProjectsEvent.Delete(project.id)) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete project")
                    }
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                enabled = name.isNotBlank(),
                onClick = { onSave(initial?.id ?: 0L, name, categoryId, color, archived) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
