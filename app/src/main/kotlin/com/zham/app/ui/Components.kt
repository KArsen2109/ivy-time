package com.zham.app.ui

import com.zham.design.system.ZhamShapes
import com.zham.design.system.GlyphIcon
import com.zham.design.system.ZhamEmojis
import com.zham.design.system.SoftPalette
import com.zham.design.system.BrightPalette
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                glyph = item.icon,
                onClick = { onSelect(if (selected) null else item.id) },
            )
        }
    }
}

/** A titled box with a visible edge, so the colour block and the emoji block can never be mixed up. */
@Composable
private fun PickerSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ZhamShapes.Field)
            .border(1.dp, ZhamTheme.extra.line, ZhamShapes.Field)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = ZhamTheme.extra.slate)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Color>, selected: Color, onSelect: (Color) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        colors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
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
                    .selectable(
                        selected = color == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(color) },
                    )
                    .semantics { contentDescription = "Colour " + (index + 1) }
            )
        }
    }
}

/** Every colour on offer in two clearly separate boxes: the bright ones first, then the softer ones. */
@Composable
fun ColorPicker(selected: Color, onSelect: (Color) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerSection("Bright colours") { Swatches(BrightPalette, selected, onSelect) }
        PickerSection("Soft and deep colours") { Swatches(SoftPalette, selected, onSelect) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlyphCell(selected: Boolean, tint: Color, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Emoji, grouped by theme, and the plain icons, each set in its own box with a title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlyphPicker(selected: String, tint: Color, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerSection("Emoji") {
            ZhamEmojis.groups.forEach { (group, emojis) ->
                Text(group, style = MaterialTheme.typography.labelMedium, color = ZhamTheme.extra.slate)
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    emojis.forEach { emoji ->
                        val key = ZhamEmojis.key(emoji)
                        GlyphCell(selected == key, tint, emoji, onClick = { onSelect(key) }) {
                            GlyphIcon(key, tint = tint, size = 22.dp)
                        }
                    }
                }
            }
        }
        PickerSection("Icons") {
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                ZhamIcons.keys.forEach { key ->
                    GlyphCell(selected == key, tint, key, onClick = { onSelect(key) }) {
                        GlyphIcon(key, tint = if (selected == key) tint else ZhamTheme.extra.slate, size = 20.dp)
                    }
                }
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
