package com.ivy.time.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.ColorDot
import com.ivy.design.system.GradientCard
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients
import com.ivy.design.system.IvyIcons
import com.ivy.design.system.SectionTitle
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.PickerItem

@Composable
fun HomeScreen(onEditEntry: (Long) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = hiltViewModel()
    HomeUi(viewModel.uiState(), viewModel::onEvent, onEditEntry, modifier)
}

@Composable
fun HomeUi(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onEditEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            Header(state, onEvent)
            Spacer(Modifier.height(12.dp))
            PeriodBar(state, onEvent)
            if (state.quickCategories.isNotEmpty() && state.running == null) {
                SectionTitle("Start tracking")
                QuickStartRow(state.quickCategories, onEvent)
            }
            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            SearchField(state.search, onEvent)
        }
        if (state.days.isEmpty()) {
            item {
                EmptyHint(
                    "No records in this period. Use + to log time you have already spent, " +
                        "or tap a category above to start a timer."
                )
            }
        }
        state.days.forEach { day ->
            item(key = "day-${day.title}") {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(day.title, Modifier.weight(1f))
                    SectionTitle(day.total)
                }
            }
            items(day.entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    onClick = { onEditEntry(entry.id) },
                    onDelete = { onEvent(HomeEvent.Delete(entry.id)) },
                )
            }
        }
    }
}

@Composable
private fun Header(state: HomeState, onEvent: (HomeEvent) -> Unit) {
    val running = state.running
    GradientCard(
        colors = if (running != null) IvyGradients.Green else IvyGradients.Ivy,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = running?.title ?: state.periodLabel,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = running?.elapsed ?: state.periodTotal,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = running?.subtitle ?: "tracked",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (running != null) {
                GradientCircleButton(IvyGradients.Red, onClick = { onEvent(HomeEvent.Stop) }) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop timer", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PeriodBar(state: HomeState, onEvent: (HomeEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(HomeEvent.ShiftPeriod(-1)) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period")
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodType.entries.forEach { type ->
                FilterChip(
                    selected = state.periodType == type,
                    onClick = { onEvent(HomeEvent.SelectPeriod(type)) },
                    label = { Text(type.name) },
                )
            }
        }
        IconButton(onClick = { onEvent(HomeEvent.ShiftPeriod(1)) }) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun QuickStartRow(categories: List<PickerItem>, onEvent: (HomeEvent) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            androidx.compose.material3.AssistChip(
                onClick = { onEvent(HomeEvent.QuickStart(category.id)) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        IvyIcons.of(category.icon),
                        contentDescription = null,
                        tint = category.color,
                    )
                },
                trailingIcon = {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = category.color)
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onEvent: (HomeEvent) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(HomeEvent.Search(it)) },
        label = { Text("Search records") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EntryRow(entry: EntryUi, onClick: () -> Unit, onDelete: () -> Unit) {
    IvyCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.icon != null) {
                Icon(IvyIcons.of(entry.icon), contentDescription = null, tint = entry.color)
            } else {
                ColorDot(entry.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.subtitle.isNotBlank()) {
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = entry.color,
                    )
                }
                Text(
                    entry.range,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                entry.duration,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!entry.running) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                }
            }
        }
    }
}
