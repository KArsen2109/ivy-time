package com.ivy.time.categories

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
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients
import com.ivy.design.system.IvyIcons
import com.ivy.time.ui.ColorPickerRow
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.IconPickerRow
import com.ivy.time.ui.IvyPalette
import com.ivy.time.ui.ScreenTitle

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

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        ) {
            item { ScreenTitle("Categories") }
            if (state.categories.isEmpty()) {
                item {
                    EmptyHint(
                        "Categories are what your time goes to: Work, Sport, Study. " +
                            "They give you one-tap tracking on the home screen."
                    )
                }
            }
            items(state.categories, key = { it.id }) { category ->
                IvyCard(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        edited = category
                        dialogVisible = true
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            IvyIcons.of(category.icon),
                            contentDescription = null,
                            tint = category.color,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                category.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${category.total} tracked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
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
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }

        GradientCircleButton(
            colors = IvyGradients.Ivy,
            onClick = {
                edited = null
                dialogVisible = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New category", tint = Color.White)
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
    var color by remember { mutableStateOf(initial?.color ?: IvyPalette.first()) }
    var icon by remember { mutableStateOf(initial?.icon ?: IvyIcons.keys.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                ColorPickerRow(IvyPalette, color, onSelect = { color = it })
                IconPickerRow(IvyIcons.keys, icon, color, onSelect = { icon = it })
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
