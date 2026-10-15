package com.ivy.time.reports

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.BarChart
import com.ivy.design.system.DonutChart
import com.ivy.design.system.IvyCard
import com.ivy.design.system.LegendRow
import com.ivy.design.system.SectionTitle
import com.ivy.design.system.Slice
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.ui.EmptyHint
import com.ivy.time.ui.ScreenTitle

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val viewModel: ReportsViewModel = hiltViewModel()
    ReportsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun ReportsUi(state: ReportsState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            ScreenTitle("Reports")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onEvent(ReportsEvent.Shift(-1)) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period")
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PeriodType.entries.forEach { type ->
                        FilterChip(
                            selected = state.periodType == type,
                            onClick = { onEvent(ReportsEvent.SelectPeriod(type)) },
                            label = { Text(type.name) },
                        )
                    }
                }
                IconButton(onClick = { onEvent(ReportsEvent.Shift(1)) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period")
                }
            }

            Text(
                state.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            IvyCard(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DonutChart(
                        slices = state.slices.map { Slice(it.minutes, it.color) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.total,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                state.average + "/day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                state.slices.forEach { row ->
                    LegendRow(
                        color = row.color,
                        label = row.name,
                        value = "${row.duration}  ${row.percent}",
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (state.slices.isEmpty()) {
                    Text(
                        "Nothing tracked in this period.",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionTitle("Per day")
            IvyCard(Modifier.padding(horizontal = 16.dp)) {
                BarChart(bars = state.bars)
            }

            if (state.projects.isNotEmpty()) {
                SectionTitle("Per project")
            }
        }
        items(state.projects, key = { it.name }) { row ->
            IvyCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LegendRow(
                    color = row.color,
                    label = row.name,
                    value = "${row.duration}  ${row.percent}",
                )
            }
        }
    }
}
