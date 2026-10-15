package com.ivy.time.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.system.ColorDot
import com.ivy.design.system.IvyIcons

@Immutable
data class PickerItem(val id: Long, val name: String, val color: Color, val icon: String? = null)

/** The horizontal chip row used to pick a category or a project. */
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = selectedId == item.id
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else item.id) },
                label = { Text(item.name) },
                leadingIcon = {
                    if (item.icon != null) {
                        Icon(IvyIcons.of(item.icon), contentDescription = null, tint = item.color)
                    } else {
                        ColorDot(item.color, size = 12.dp)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = item.color.copy(alpha = 0.25f),
                ),
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
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(colors) { _, color ->
            Box(
                Modifier
                    .size(36.dp)
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
                        if (key == selected) tint.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(IvyIcons.of(key), contentDescription = key, tint = tint)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A labelled value that opens a picker when tapped. */
@Composable
fun FieldButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        trailing?.invoke()
    }
}
