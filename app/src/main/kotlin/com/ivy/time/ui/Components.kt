package com.ivy.time.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivy.design.system.ChoiceChip
import com.ivy.design.system.IvyIcons
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.Spacing

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
                icon = item.icon?.let(IvyIcons::of),
                onClick = { onSelect(if (selected) null else item.id) },
            )
        }
    }
}

@Composable
fun ColorPickerRow(
    colors: List<Color>,
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { color ->
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

@Composable
fun IconPickerRow(
    keys: List<String>,
    selected: String,
    tint: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(keys) { key ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (key == selected) tint.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    IvyIcons.of(key),
                    contentDescription = key,
                    tint = if (key == selected) tint else IvyTheme.extra.slate,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
