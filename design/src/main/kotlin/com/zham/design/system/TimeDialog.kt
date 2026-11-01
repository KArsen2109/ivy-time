package com.zham.design.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalTime

/**
 * Picks a time on a clock face, or by typing it. On the face the hour is chosen first and then the
 * minute; in typing mode the hour and the minute are two separate fields, each with the number
 * pad, so they never run together as one four-digit number.
 *
 * The set button stays disabled, and [blockedHint] says why, while [isAllowed] rejects the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    title: String,
    initial: LocalTime,
    now: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSet: (LocalTime) -> Unit,
    isAllowed: (LocalTime) -> Boolean = { true },
    blockedHint: String = "",
) {
    // "Now" starts the picker over from the current time; the picker state is rebuilt for a new seed.
    var seed by remember { mutableStateOf(initial) }
    var typing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        key(seed) {
            val state = rememberTimePickerState(seed.hour, seed.minute, is24Hour)
            val time = LocalTime.of(state.hour, state.minute)
            val blocked = !isAllowed(time)

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .clip(ZhamShapes.Card)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (typing) TimeInput(state) else TimePicker(state)
                }

                if (blocked) {
                    Text(
                        blockedHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { typing = !typing }) {
                        Icon(
                            if (typing) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                            contentDescription = if (typing) "Use the clock" else "Type the time",
                        )
                    }
                    QuietButton("Now", { seed = now }, Modifier.padding(start = 4.dp))
                    Box(Modifier.weight(1f))
                    QuietButton("Cancel", onDismiss)
                    PrimaryButton(
                        text = "Set time",
                        onClick = { onSet(time) },
                        enabled = !blocked,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
