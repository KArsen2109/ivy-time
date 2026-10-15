package com.ivy.time.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivy.design.system.SectionTitle
import com.ivy.time.data.ThemeMode
import com.ivy.time.ui.ScreenTitle
import com.ivy.time.ui.SettingRow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state = viewModel.uiState()
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
            viewModel.onEvent(SettingsEvent.ExportDone(written))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (content != null) viewModel.onEvent(SettingsEvent.ImportCsv(content))
    }

    SettingsUi(
        state = state,
        onEvent = viewModel::onEvent,
        onExport = { exportLauncher.launch("ivy-time-backup.csv") },
        onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
        modifier = modifier,
    )
}

@Composable
fun SettingsUi(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            ScreenTitle("Settings")

            SectionTitle("Theme")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.theme == mode,
                        onClick = { onEvent(SettingsEvent.SetTheme(mode)) },
                        label = { Text(if (mode == ThemeMode.AmoledDark) "Amoled" else mode.name) },
                    )
                }
            }

            SectionTitle("Week starts on")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                    FilterChip(
                        selected = state.weekStart == day,
                        onClick = { onEvent(SettingsEvent.SetWeekStart(day)) },
                        label = { Text(day.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            SettingRow(
                title = "24-hour time",
                subtitle = if (state.use24Hours) "14:30" else "2:30 PM",
                onClick = null,
                trailing = {
                    Switch(
                        checked = state.use24Hours,
                        onCheckedChange = { onEvent(SettingsEvent.SetUse24Hours(it)) },
                    )
                },
            )

            SectionTitle("Backup")
            SettingRow(
                title = "Export to CSV",
                subtitle = "${state.entryCount} records",
                onClick = onExport,
            )
            SettingRow(
                title = "Import from CSV",
                subtitle = "Adds records; missing categories are created",
                onClick = onImport,
            )

            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            SectionTitle("About")
            SettingRow(
                title = "Ivy Time",
                subtitle = "Open source time tracker built like Ivy Wallet",
                onClick = null,
            )
        }
    }
}
