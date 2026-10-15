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
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.LegendRow
import com.ivy.design.system.ScreenHeading
import com.ivy.design.system.SectionLabel
import com.ivy.design.system.Segmented
import com.ivy.design.system.Slice
import com.ivy.design.system.Spacing
import com.ivy.time.domain.usecase.PeriodType

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val viewModel: ReportsViewModel = hiltViewModel()
    ReportsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun ReportsUi(state: ReportsState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("Reports")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onEvent(ReportsEvent.Shift(-1)) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period")
                }
                Segmented(
                    options = PeriodType.entries,
                    selected = state.periodType,
                    label = { it.name },
                    onSelect = { onEvent(ReportsEvent.SelectPeriod(it)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEvent(ReportsEvent.Shift(1)) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period")
                }
            }

            Text(
                state.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                color = IvyTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 8.dp),
            )

            IvyCard(Modifier.padding(horizontal = Spacing.Gutter)) {
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
                                color = IvyTheme.extra.slate,
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
                        color = IvyTheme.extra.slate,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionLabel("Per day")
            IvyCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                BarChart(bars = state.bars)
            }

            if (state.projects.isNotEmpty()) {
                SectionLabel("Per project")
            }
        }
        items(state.projects, key = { it.name }) { row ->
            IvyCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp)) {
                LegendRow(
                    color = row.color,
                    label = row.name,
                    value = "${row.duration}  ${row.percent}",
                )
            }
        }
    }
}
