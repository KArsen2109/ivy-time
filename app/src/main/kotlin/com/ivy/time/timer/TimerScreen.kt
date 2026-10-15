package com.ivy.time.timer

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.ChoiceChip
import com.ivy.design.system.ColorDot
import com.ivy.design.system.EmptyNote
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyIcons
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.PrimaryButton
import com.ivy.design.system.ProgressRing
import com.ivy.design.system.ScreenHeading
import com.ivy.design.system.SectionLabel
import com.ivy.design.system.Spacing

@Composable
fun TimerScreen(onEditRecord: (Long) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TimerViewModel = hiltViewModel()
    TimerUi(viewModel.uiState(), viewModel::onEvent, onEditRecord, modifier)
}

@Composable
fun TimerUi(
    state: TimerState,
    onEvent: (TimerEvent) -> Unit,
    onEditRecord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("Timer")

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProgressRing(
                    fraction = state.hourFraction,
                    color = if (state.running) IvyTheme.extra.signal else state.color,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.elapsed,
                            style = MaterialTheme.typography.displayMedium,
                            color = if (state.running) {
                                IvyTheme.extra.signal
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = IvyTheme.extra.slate,
                        )
                    }
                }
            }

            if (!state.running) {
                SectionLabel("Track")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.Gutter),
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        ChoiceChip(
                            label = category.name,
                            selected = state.selectedCategoryId == category.id,
                            color = category.color,
                            icon = category.icon?.let(IvyIcons::of),
                            onClick = { onEvent(TimerEvent.SelectCategory(category.id)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onEvent(TimerEvent.SetNote(it)) },
                    label = { Text("What are you working on?") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Gutter, vertical = 14.dp),
                )
            } else {
                Spacer(Modifier.height(24.dp))
            }

            PrimaryButton(
                text = if (state.running) "Stop" else "Start",
                onClick = { onEvent(TimerEvent.Toggle) },
                icon = if (state.running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter),
            )

            state.error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 10.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Today", Modifier.weight(1f))
                SectionLabel(state.todayTotal)
            }
            if (state.records.isEmpty()) {
                EmptyNote("Nothing tracked today yet.")
            }
        }
        items(state.records, key = { it.id }) { record ->
            IvyCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = { onEditRecord(record.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(
                        if (record.running) IvyTheme.extra.signal else record.color,
                        size = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            record.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = IvyTheme.extra.slate,
                        )
                    }
                    Text(record.duration, style = MaterialTheme.typography.titleMedium)
                    if (record.running) {
                        IconButton(onClick = { onEvent(TimerEvent.Toggle) }) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Stop timer",
                                tint = IvyTheme.extra.signal,
                            )
                        }
                    } else {
                        IconButton(onClick = { onEvent(TimerEvent.Resume(record.id)) }) {
                            Icon(Icons.Rounded.Replay, contentDescription = "Track this again")
                        }
                        IconButton(onClick = { onEvent(TimerEvent.Delete(record.id)) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                        }
                    }
                }
            }
        }
    }
}
