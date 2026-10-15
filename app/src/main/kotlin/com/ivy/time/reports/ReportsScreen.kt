package com.ivy.time.reports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.ColorDot
import com.ivy.design.system.GradientCard
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvyGradients

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val viewModel: ReportsViewModel = hiltViewModel()
    ReportsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun ReportsUi(state: ReportsState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            Column {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReportPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = state.period == period,
                            onClick = { onEvent(ReportsEvent.SelectPeriod(period)) },
                            label = { Text(if (period == ReportPeriod.Week) "This week" else "This month") },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                GradientCard(IvyGradients.Ivy, Modifier.padding(horizontal = 16.dp)) {
                    Text("Total", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.total,
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        itemsIndexed(state.rows, key = { index, row -> "$index-${row.name}" }) { _, row ->
            IvyCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(row.color ?: MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(row.duration, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Spacer(
                        Modifier
                            .fillMaxWidth(row.fraction.coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .background(row.color ?: MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
        if (state.rows.isEmpty()) {
            item {
                Text(
                    "Nothing tracked in this period.",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}
