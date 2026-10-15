package com.zham.app.reports

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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zham.design.system.BarChart
import com.zham.design.system.ChoiceChip
import com.zham.design.system.ColorDot
import com.zham.design.system.DonutChart
import com.zham.design.system.EmptyNote
import com.zham.design.system.FieldButton
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamIcons
import com.zham.design.system.ZhamShapes
import com.zham.design.system.ZhamTheme
import com.zham.design.system.LegendRow
import com.zham.design.system.PrimaryButton
import com.zham.design.system.QuietButton
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.Slice
import com.zham.design.system.Spacing
import com.zham.app.ui.CalendarSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val viewModel: ReportsViewModel = hiltViewModel()
    ReportsUi(viewModel.uiState(), viewModel::onEvent, modifier)
}

@Composable
fun ReportsUi(state: ReportsState, onEvent: (ReportsEvent) -> Unit, modifier: Modifier = Modifier) {
    var filterVisible by remember { mutableStateOf(false) }

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
                    Text("Reports", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhamTheme.extra.slate,
                    )
                }
                if (state.canShift) {
                    IconButton(onClick = { onEvent(ReportsEvent.Shift(-1)) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous period")
                    }
                    IconButton(onClick = { onEvent(ReportsEvent.Shift(1)) }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next period")
                    }
                }
                IconButton(onClick = { filterVisible = true }) {
                    Icon(
                        Icons.Rounded.FilterAlt,
                        contentDescription = "Filter",
                        tint = if (state.filterActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Segmented(
                options = ReportPeriod.entries,
                selected = state.period,
                label = { it.name },
                onSelect = { onEvent(ReportsEvent.SelectPeriod(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter),
            )

            AppliedFilters(state, onEvent)
            Spacer(Modifier.height(16.dp))

            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DonutChart(slices = state.slices.map { Slice(it.minutes, it.color) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.total,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                state.average,
                                style = MaterialTheme.typography.bodySmall,
                                color = ZhamTheme.extra.slate,
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
                if (!state.hasTracked) {
                    Text(
                        "Nothing tracked with these filters, so all of it is grey.",
                        color = ZhamTheme.extra.slate,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.singleDay) {
                SectionLabel("Records")
                if (state.records.isEmpty()) EmptyNote("Nothing tracked on this day.")
            } else {
                SectionLabel("Hours per day")
                ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            state.barCaption,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                        Text(
                            state.barPeak,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BarChart(bars = state.bars, valueLabels = state.barValues)
                }
            }

            if (!state.singleDay && state.projects.isNotEmpty()) {
                SectionLabel("Per project")
            }
        }

        items(state.records) { record ->
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(record.color)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(record.path, record.range).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
                        )
                        if (record.description.isNotBlank()) {
                            Text(record.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(record.duration, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (!state.singleDay) {
            items(state.projects) { row ->
                ZhamCard(Modifier.padding(horizontal = Spacing.Gutter, vertical = 5.dp)) {
                    LegendRow(
                        color = row.color,
                        label = row.name,
                        value = "${row.duration}  ${row.percent}",
                    )
                }
            }
        }
    }

    if (filterVisible) {
        FilterSheet(
            state = state,
            onApply = { ids, from, to ->
                onEvent(ReportsEvent.ApplyFilter(ids, from, to))
                filterVisible = false
            },
            onClear = {
                onEvent(ReportsEvent.ClearFilter)
                filterVisible = false
            },
            onDismiss = { filterVisible = false },
        )
    }
}

/** What is narrowing the numbers, always on screen, so a surprising total can be explained. */
@Composable
private fun AppliedFilters(state: ReportsState, onEvent: (ReportsEvent) -> Unit) {
    val chosen = state.categories.filter { it.id in state.selectedCategoryIds }
    val rangeActive = state.period == ReportPeriod.Custom &&
        state.customFrom != null && state.customTo != null
    if (chosen.isEmpty() && !rangeActive) return

    val format = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
    ) {
        items(chosen, key = { it.id }) { category ->
            ChoiceChip(
                label = category.name + "  ✕",
                selected = true,
                color = category.color,
                icon = category.icon?.let(ZhamIcons::of),
                onClick = { onEvent(ReportsEvent.RemoveCategory(category.id)) },
            )
        }
        if (rangeActive) {
            item {
                ChoiceChip(
                    label = format.format(state.customFrom) + " – " +
                        format.format(state.customTo) + "  ✕",
                    selected = true,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onEvent(ReportsEvent.ClearRange) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: ReportsState,
    onApply: (Set<Long>, LocalDate?, LocalDate?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(state.selectedCategoryIds) }
    var from by remember { mutableStateOf(state.customFrom) }
    var to by remember { mutableStateOf(state.customTo) }
    var picking by remember { mutableStateOf<String?>(null) }
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ZhamShapes.Sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Gutter)
                .padding(bottom = 28.dp)
        ) {
            Text("Filter", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Narrow the report to the categories and dates you care about.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZhamTheme.extra.slate,
            )

            Text(
                "Categories",
                style = MaterialTheme.typography.titleSmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.categories, key = { it.id }) { category ->
                    ChoiceChip(
                        label = category.name,
                        selected = category.id in chosen,
                        color = category.color,
                        icon = category.icon?.let(ZhamIcons::of),
                        onClick = {
                            chosen = if (category.id in chosen) chosen - category.id else chosen + category.id
                        },
                    )
                }
            }
            Text(
                if (chosen.isEmpty()) "All categories" else "${chosen.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(top = 6.dp),
            )

            Text(
                "Dates",
                style = MaterialTheme.typography.titleSmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldButton(
                    label = "From",
                    value = from?.let(format::format) ?: "Any",
                    onClick = { picking = "from" },
                    modifier = Modifier.weight(1f),
                )
                FieldButton(
                    label = "To",
                    value = to?.let(format::format) ?: "Any",
                    onClick = { picking = "to" },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Clear all", onClear, Modifier.weight(1f))
                PrimaryButton("Apply", { onApply(chosen, from, to) }, Modifier.weight(1f))
            }
        }
    }

    when (picking) {
        "from" -> CalendarSheet(
            title = "From",
            selected = from ?: state.today,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                from = it
                if (to == null || to!!.isBefore(it)) to = it
                picking = null
            },
            onDismiss = { picking = null },
        )
        "to" -> CalendarSheet(
            title = "To",
            selected = to ?: state.today,
            today = state.today,
            marks = state.marks,
            weekStart = state.weekStart,
            onPick = {
                to = it
                if (from == null || from!!.isAfter(it)) from = it
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
