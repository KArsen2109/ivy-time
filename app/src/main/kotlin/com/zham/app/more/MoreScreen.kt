package com.zham.app.more

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import android.content.Context
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
import com.zham.design.system.CountTile
import com.zham.design.system.DotStrip
import com.zham.design.system.ZhamCard
import com.zham.design.system.ZhamSwitch
import com.zham.design.system.ZhamTheme
import com.zham.design.system.MeterBar
import com.zham.design.system.ScreenHeading
import com.zham.design.system.SectionLabel
import com.zham.design.system.Segmented
import com.zham.design.system.SettingRow
import com.zham.design.system.Spacing
import com.zham.data.ThemeMode
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

    val fullLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = writeText(context, uri) { viewModel.buildFullBackup() }
            viewModel.onEvent(MoreEvent.ExportDone(written))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = writeText(context, uri) { viewModel.buildCsv() }
            viewModel.onEvent(MoreEvent.ExportDone(written))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = readText(context, uri)
            if (content != null) {
                viewModel.onEvent(MoreEvent.ImportCsv(content))
            } else {
                viewModel.onEvent(MoreEvent.ImportUnreadable)
            }
        }
    }

    MoreUi(
        state = viewModel.uiState(),
        onEvent = viewModel::onEvent,
        onOpenCategories = onOpenCategories,
        onOpenProjects = onOpenProjects,
        onOpenGoals = onOpenGoals,
        onExportFull = { fullLauncher.launch("zham-backup.json") },
        onExport = { exportLauncher.launch("zham-records.csv") },
        // A backup may be offered with any file type, so none is filtered out.
        onImport = { importLauncher.launch(arrayOf("*/*")) },
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
    onExportFull: () -> Unit,
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
                                    color = ZhamTheme.extra.slate,
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
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Week starts on", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Used by the week view and by goals",
                            style = MaterialTheme.typography.bodySmall,
                            color = ZhamTheme.extra.slate,
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
                            color = ZhamTheme.extra.slate,
                        )
                    }
                    ZhamSwitch(
                        checked = state.use24Hours,
                        onCheckedChange = { onEvent(MoreEvent.SetUse24Hours(it)) },
                    )
                }
            }

            SectionLabel("Your data")
            ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
                SettingRow(
                    title = "Back up everything",
                    subtitle = "Records, plans, goals, categories and projects in one file",
                    onClick = onExportFull,
                    horizontalPadding = 0.dp,
                )
                SettingRow(
                    title = "Export records to CSV",
                    subtitle = "${state.recordCount} records, for spreadsheets",
                    onClick = onExport,
                    horizontalPadding = 0.dp,
                )
                SettingRow(
                    title = "Restore or import",
                    subtitle = "Takes a full backup or a CSV and adds only what is missing",
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
                "Zham " + versionName(LocalContext.current) + " · everything stays on this phone",
                style = MaterialTheme.typography.bodySmall,
                color = ZhamTheme.extra.slate,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 20.dp),
            )
        }
    }
}

/** The most text an import will read; a real backup is far smaller, so a bigger file is not one. */
private const val MAX_IMPORT_CHARS = 20_000_000

/** Writes the text off the main thread; true only when the file was really opened and written. */
private suspend fun writeText(context: Context, uri: Uri, text: suspend () -> String): Boolean {
    val body = text()
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } != null
        } catch (e: Exception) {
            false
        }
    }
}

/** Reads the file off the main thread, or null when it cannot be read or is implausibly large. */
private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                out.append(buffer, 0, read)
                if (out.length > MAX_IMPORT_CHARS) return@use null
            }
            out.toString()
        }
    } catch (e: Exception) {
        null
    }
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
