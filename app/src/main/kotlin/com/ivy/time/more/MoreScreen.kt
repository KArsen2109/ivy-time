package com.ivy.time.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.CountTile
import com.ivy.design.system.DotStrip
import com.ivy.design.system.IvyCard
import com.ivy.design.system.IvySwitch
import com.ivy.design.system.IvyTheme
import com.ivy.design.system.MeterBar
import com.ivy.design.system.ScreenHeading
import com.ivy.design.system.SectionLabel
import com.ivy.design.system.Segmented
import com.ivy.design.system.SettingRow
import com.ivy.design.system.Spacing
import com.ivy.time.data.ThemeMode
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun MoreScreen(
    onOpenCategories: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MoreViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.buildCsv()
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            }.isSuccess
            viewModel.onEvent(MoreEvent.ExportDone(written))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (content != null) viewModel.onEvent(MoreEvent.ImportCsv(content))
    }

    MoreUi(
        state = viewModel.uiState(),
        onEvent = viewModel::onEvent,
        onOpenCategories = onOpenCategories,
        onOpenProjects = onOpenProjects,
        onOpenGoals = onOpenGoals,
        onExport = { exportLauncher.launch("ivy-time-backup.csv") },
        onImport = {
            importLauncher.launch(
                arrayOf("text/csv", "text/comma-separated-values", "text/plain")
            )
        },
        modifier = modifier,
    )
}

@Composable
fun MoreUi(
    state: MoreState,
    onEvent: (MoreEvent) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenGoals: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("More")

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CountTile(
                    count = state.categoryCount.toString(),
                    label = "Categories",
                    onClick = onOpenCategories,
                    modifier = Modifier.weight(1f),
                    preview = { DotStrip(state.categoryColors) },
                )
                CountTile(
                    count = state.projectCount.toString(),
                    label = "Projects",
                    onClick = onOpenProjects,
                    modifier = Modifier.weight(1f),
                    preview = { DotStrip(state.projectColors) },
                )
                CountTile(
                    count = state.goalCount.toString(),
                    label = "Goals",
                    onClick = onOpenGoals,
                    modifier = Modifier.weight(1f),
                    preview = {
                        if (state.goalCount > 0) {
                            Column {
                                MeterBar(
                                    fraction = state.goalProgress,
                                    color = MaterialTheme.colorScheme.primary,
                                    height = 4.dp,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "${state.goalsMet} on track",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IvyTheme.extra.slate,
                                )
                            }
                        }
                    },
                )
            }

            SectionLabel("Appearance")
            Segmented(
                options = ThemeMode.entries,
                selected = state.theme,
                label = { if (it == ThemeMode.AmoledDark) "Amoled" else it.name },
                onSelect = { onEvent(MoreEvent.SetTheme(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter),
            )
            Spacer(Modifier.height(10.dp))
            IvyCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Week starts on", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Used by the week view and by goals",
                            style = MaterialTheme.typography.bodySmall,
                            color = IvyTheme.extra.slate,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Segmented(
                        options = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
                        selected = state.weekStart,
                        label = { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) },
                        onSelect = { onEvent(MoreEvent.SetWeekStart(it)) },
                        modifier = Modifier.width(132.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("24-hour time", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.use24Hours) "14:30" else "2:30 PM",
                            style = MaterialTheme.typography.bodySmall,
                            color = IvyTheme.extra.slate,
                        )
                    }
                    IvySwitch(
                        checked = state.use24Hours,
                        onCheckedChange = { onEvent(MoreEvent.SetUse24Hours(it)) },
                    )
                }
            }

            SectionLabel("Your data")
            IvyCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                SettingRow(
                    title = "Export to CSV",
                    subtitle = "${state.recordCount} records, ${state.categoryCount} categories",
                    onClick = onExport,
                    horizontalPadding = 0.dp,
                )
                SettingRow(
                    title = "Import from CSV",
                    subtitle = "Adds records and creates any missing categories",
                    onClick = onImport,
                    horizontalPadding = 0.dp,
                )
            }

            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 10.dp),
                )
            }

            Text(
                "Zham 0.5 · open source",
                style = MaterialTheme.typography.bodySmall,
                color = IvyTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 20.dp),
            )
        }
    }
}
