package com.zham.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zham.design.system.ChoiceChip
import com.zham.design.system.ZhamIcons
import com.zham.design.system.ZhamTheme
import com.zham.design.system.Spacing

@Immutable
data class PickerItem(val id: Long, val name: String, val color: Color, val icon: String? = null)

/** The chip row used to pick a category or a project. Tapping the selected one clears it. */
@Composable
fun PickerRow(
    items: List<PickerItem>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = selectedId == item.id
            ChoiceChip(
                label = item.name,
                selected = selected,
                color = item.color,
                icon = item.icon?.let(ZhamIcons::of),
                onClick = { onSelect(if (selected) null else item.id) },
            )
        }
    }
}

/** All the colours at once, wrapping onto as many rows as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerRow(
    colors: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (color == selected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(color) }
            )
        }
    }
}

/** All the icons at once, so none hides off the edge of a scrolling row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerRow(
    keys: List<String>,
    selected: String,
    tint: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (key == selected) tint.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ZhamIcons.of(key),
                    contentDescription = key,
                    tint = if (key == selected) tint else ZhamTheme.extra.slate,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Asks before something is deleted. Returns the function to call when the delete button is
 * tapped; the dialog is drawn by this call, so use it in the same place as the button.
 */
@Composable
fun rememberDeleteConfirm(title: String, message: String, onConfirm: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onConfirm()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
    return { asking = true }
}
