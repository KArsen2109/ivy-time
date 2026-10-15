package com.zham.app.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.CategoryPalette
import com.zham.design.system.EmptyNote
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamIcons
import com.zham.design.system.ZhamTheme
import com.zham.design.system.ScreenHeading
import com.zham.design.system.Spacing
import com.zham.app.ui.ColorPickerRow
import com.zham.app.ui.IconPickerRow

@Composable
fun CategoriesScreen(modifier: Modifier = Modifier) {
    val viewModel: CategoriesViewModel = hiltViewModel()
    CategoriesUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun CategoriesUi(
    state: CategoriesState,
    onEvent: (CategoriesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var edited by remember { mutableStateOf<CategoryItem?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("Categories") {
                IconButton(
                    onClick = {
                        edited = null
                        dialogVisible = true
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New category")
                }
            }
            if (state.categories.isEmpty()) {
                EmptyNote(
                    "Categories are what your time goes to: Work, Sport, Study. " +
                        "They colour the timeline and give you one-tap tracking."
                )
            }
        }
        items(state.categories, key = { it.id }) { category ->
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = {
                    edited = category
                    dialogVisible = true
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        ZhamIcons.of(category.icon),
                        contentDescription = null,
                        tint = category.color,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            category.total + " tracked" + when (category.projectCount) {
                                0 -> ""
                                1 -> " · 1 project"
                                else -> " · ${category.projectCount} projects"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    IconButton(onClick = { onEvent(CategoriesEvent.Delete(category.id)) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete category")
                    }
                }
            }
        }
        state.error?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
                )
            }
        }
    }

    if (dialogVisible) {
        CategoryDialog(
            initial = edited,
            onDismiss = { dialogVisible = false },
            onSave = { id, name, color, icon ->
                onEvent(CategoriesEvent.Save(id, name, color, icon))
                dialogVisible = false
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    initial: CategoryItem?,
    onDismiss: () -> Unit,
    onSave: (Long, String, Color, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: CategoryPalette.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: ZhamIcons.keys.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New category" else "Edit category") },
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
                ColorPickerRow(CategoryPalette, color, onSelect = { color = it })
                IconPickerRow(ZhamIcons.keys, icon, color, onSelect = { icon = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial?.id ?: 0L, name, color, icon) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
