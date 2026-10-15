package com.ivy.time.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.EmptyNote
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.MeterBar
import com.ivy.design.system.SectionLabel
import com.ivy.design.system.Spacing
import com.ivy.design.system.StatPair
import com.ivy.design.system.WeekStrip
import java.time.LocalDate

@Composable
fun WeekScreen(onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: WeekViewModel = hiltViewModel()
    WeekUi(viewModel.uiState(), viewModel::onEvent, onOpenDay, modifier)
}

@Composable
fun WeekUi(
    state: WeekState,
    onEvent: (WeekEvent) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.Gutter, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = IvyTheme.extra.slate,
                    )
                }
                IconButton(onClick = { onEvent(WeekEvent.ShiftWeek(-1)) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous week")
                }
                IconButton(onClick = { onEvent(WeekEvent.ShiftWeek(1)) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next week")
                }
            }

            Row(
                Modifier.padding(horizontal = Spacing.Gutter, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                StatPair("planned", state.plannedLabel, MaterialTheme.colorScheme.onSurface)
                StatPair("tracked", state.trackedLabel, MaterialTheme.colorScheme.onSurface)
                StatPair(
                    "of plan kept",
                    "${state.keptPercent}%",
                    MaterialTheme.colorScheme.primary,
                )
            }

            Column(Modifier.padding(horizontal = Spacing.Gutter)) {
                WeekStrip(
                    days = state.days,
                    fromHour = 6,
                    toHour = 24,
                    onDayClick = { index -> state.dates.getOrNull(index)?.let(onOpenDay) },
                )
            }

            Text(
                "Busiest day: " + state.busiestLabel,
                style = MaterialTheme.typography.bodySmall,
                color = IvyTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 12.dp),
            )

            SectionLabel("Goals")
            if (state.goals.isEmpty()) {
                EmptyNote("No goals yet. Set them in More to hold a category to a weekly target.")
            }
        }
        items(state.goals, key = { it.id }) { goal ->
            IvyCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        goal.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (goal.met) "on track" else "behind",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (goal.met) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            IvyTheme.extra.slate
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                MeterBar(
                    fraction = goal.fraction,
                    color = if (goal.met) MaterialTheme.colorScheme.primary else goal.color,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    goal.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = IvyTheme.extra.slate,
                )
            }
        }
    }
}
