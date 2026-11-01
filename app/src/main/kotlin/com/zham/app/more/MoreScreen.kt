package com.zham.app.more

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.items
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

            SectionLabel("Reminders")
            ReminderSettings(state, onEvent)

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

private val DEFAULT_REMINDER_CHOICES = listOf(-1, 0, 5, 10, 15, 30, 60)

private fun reminderChoiceLabel(minutes: Int): String = when (minutes) {
    -1 -> "Off"
    0 -> "At start"
    60 -> "1 h before"
    else -> "$minutes min before"
}

/** The default lead time, whether the phone will actually deliver reminders, and a way to try one. */
@Composable
private fun ReminderSettings(state: MoreState, onEvent: (MoreEvent) -> Unit) {
    val context = LocalContext.current
    // The phone's settings are changed outside the app, so the status is read again on every return.
    var refresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(refresh) {
        context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()
    }
    val exactOn = remember(refresh) {
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
    }
    val batteryFree = remember(refresh) {
        context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    fun open(intent: android.content.Intent) {
        // A few phone makers leave a settings page out, so a failed jump must not crash.
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    ZhamCard(Modifier.padding(horizontal = Spacing.Gutter)) {
        Text("Default reminder for a new block", style = MaterialTheme.typography.bodyLarge)
        Text(
            "You can change it for any block when you plan it.",
            style = MaterialTheme.typography.bodySmall,
            color = ZhamTheme.extra.slate,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DEFAULT_REMINDER_CHOICES) { minutes ->
                com.zham.design.system.ChoiceChip(
                    label = reminderChoiceLabel(minutes),
                    selected = state.defaultReminder == minutes,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onEvent(MoreEvent.SetDefaultReminder(minutes)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "Notifications",
            subtitle = if (notificationsOn) "Allowed" else "Blocked. Tap to allow them.",
            onClick = {
                open(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
            horizontalPadding = 0.dp,
        )
        SettingRow(
            title = "Exact timing",
            subtitle = if (exactOn) "Allowed" else "Not allowed, so reminders may be a few minutes late. Tap to allow.",
            onClick = {
                open(
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.packageName),
                    )
                )
            },
            horizontalPadding = 0.dp,
        )
        SettingRow(
            title = "Battery",
            subtitle = if (batteryFree) "Not restricted" else "May be restricted. Set Zham to unrestricted if reminders come late.",
            onClick = {
                open(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            horizontalPadding = 0.dp,
        )
        SettingRow(
            title = "Send a test reminder",
            subtitle = "Arrives in about 20 seconds",
            onClick = { onEvent(MoreEvent.TestReminder) },
            horizontalPadding = 0.dp,
        )
    }
}
