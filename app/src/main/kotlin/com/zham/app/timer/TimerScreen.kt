package com.zham.app.timer

import androidx.compose.ui.text.style.TextOverflow
import com.zham.app.ui.rememberDeleteConfirm
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.ChoiceChip
import com.zham.design.system.ColorDot
import com.zham.design.system.EmptyNote
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamIcons
import com.zham.design.system.ZhamTheme
import com.zham.design.system.PrimaryButton
import com.zham.design.system.ProgressRing
import com.zham.design.system.ScreenHeading
import com.zham.design.system.SectionLabel
import com.zham.design.system.Spacing

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
                // The ring, the digits and the button all wear the category being tracked.
                ProgressRing(
                    fraction = state.hourFraction,
                    color = state.color,
                    size = 232.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    ) {
                        Text(
                            text = state.elapsed,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                            maxLines = 1,
                            softWrap = false,
                            color = if (state.running) state.color else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
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
                            icon = category.icon?.let(ZhamIcons::of),
                            onClick = { onEvent(TimerEvent.SelectCategory(category.id)) },
                        )
                    }
                }
                SectionLabel("Project in " + state.categoryName)
                if (state.projects.isEmpty()) {
                    EmptyNote(
                        "No projects in " + state.categoryName + " yet. " +
                            "Add one in More, under Projects, and choose this category."
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            ChoiceChip(
                                label = project.name,
                                selected = state.selectedProjectId == project.id,
                                color = state.color,
                                onClick = {
                                    onEvent(
                                        TimerEvent.SelectProject(
                                            if (state.selectedProjectId == project.id) null else project.id
                                        )
                                    )
                                },
                            )
                        }
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
                text = if (state.running) "Stop" else if (state.selectedCategoryId == null) "Start" else "Start " + state.categoryName,
                color = state.color,
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
            ZhamCard(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp),
                onClick = { onEditRecord(record.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(
                        if (record.running) ZhamTheme.extra.signal else record.color,
                        size = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            record.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    Text(record.duration, style = MaterialTheme.typography.titleMedium)
                    if (record.running) {
                        IconButton(onClick = { onEvent(TimerEvent.Toggle) }) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Stop timer",
                                tint = ZhamTheme.extra.signal,
                            )
                        }
                    } else {
                        IconButton(onClick = { onEvent(TimerEvent.Resume(record.id)) }) {
                            Icon(Icons.Rounded.Replay, contentDescription = "Track this again")
                        }
                        val askDelete = rememberDeleteConfirm(
                            "Delete this record?", "The tracked time is removed.",
                        ) { onEvent(TimerEvent.Delete(record.id)) }
                        IconButton(onClick = askDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                        }
                    }
                }
            }
        }
    }
}
