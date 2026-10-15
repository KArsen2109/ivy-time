package com.ivy.time.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.GradientCard
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients
import com.ivy.design.system.SectionTitle
import com.ivy.design.system.ColorDot

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = hiltViewModel()
    HomeUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun HomeUi(state: HomeState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        ) {
            item {
                Header(state, onStop = { onEvent(HomeEvent.Stop) })
                state.error?.let { ErrorText(it) }
            }
            state.days.forEach { day ->
                item(key = "day-${day.title}") {
                    Row(Modifier.padding(top = 16.dp)) {
                        SectionTitle(day.title, Modifier.weight(1f))
                        SectionTitle(day.total)
                    }
                }
                items(day.entries, key = { it.id }) { entry ->
                    EntryRow(entry, onDelete = { onEvent(HomeEvent.Delete(entry.id)) })
                }
            }
        }

        GradientCircleButton(
            colors = IvyGradients.Ivy,
            onClick = { dialogVisible = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New entry", tint = Color.White)
        }
    }

    if (dialogVisible) {
        NewEntryDialog(
            projects = state.projects,
            timerRunning = state.running != null,
            onDismiss = { dialogVisible = false },
            onStart = { projectId, note ->
                onEvent(HomeEvent.Start(projectId, note))
                dialogVisible = false
            },
            onLog = { projectId, note, minutes ->
                onEvent(HomeEvent.Log(projectId, note, minutes))
                dialogVisible = false
            },
        )
    }
}

@Composable
private fun Header(state: HomeState, onStop: () -> Unit) {
    val running = state.running
    GradientCard(
        colors = if (running != null) IvyGradients.Green else IvyGradients.Ivy,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Column {
                    Text(
                        text = if (running != null) running.title else "Today",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = running?.elapsed ?: state.todayTotal,
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            if (running != null) {
                GradientCircleButton(
                    colors = IvyGradients.Red,
                    onClick = onStop,
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop timer", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(error: HomeError) {
    Text(
        text = when (error) {
            HomeError.TimerAlreadyRunning -> "A timer is already running. Stop it first."
            HomeError.InvalidDuration -> "Enter a duration greater than 0 minutes."
        },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun EntryRow(entry: EntryUi, onDelete: () -> Unit) {
    IvyCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(entry.color ?: MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    entry.range,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(entry.duration, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!entry.running) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete entry")
                }
            }
        }
    }
}

@Composable
private fun NewEntryDialog(
    projects: List<ProjectUi>,
    timerRunning: Boolean,
    onDismiss: () -> Unit,
    onStart: (projectId: Long?, note: String) -> Unit,
    onLog: (projectId: Long?, note: String, minutes: Long) -> Unit,
) {
    var projectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var minutesText by rememberSaveable { mutableStateOf("") }
    val minutes = minutesText.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (projects.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(projects, key = { it.id }) { project ->
                            FilterChip(
                                selected = projectId == project.id,
                                onClick = { projectId = if (projectId == project.id) null else project.id },
                                label = { Text(project.name) },
                                leadingIcon = { ColorDot(project.color, size = 12.dp) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What are you working on?") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes (to log past time)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = minutes != null,
                    onClick = { onLog(projectId, note, minutes ?: 0) },
                ) { Text("Log") }
                TextButton(
                    enabled = !timerRunning,
                    onClick = { onStart(projectId, note) },
                ) { Text("Start timer") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
