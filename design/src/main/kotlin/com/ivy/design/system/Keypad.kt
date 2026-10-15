package com.ivy.design.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalTime

/** What four typed digits mean as a time. Digits enter from the right, like a calculator. */
fun digitsToTime(digits: String): LocalTime? {
    val padded = digits.padStart(4, '0').takeLast(4)
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2).toInt()
    return if (hours in 0..23 && minutes in 0..59) LocalTime.of(hours, minutes) else null
}

/**
 * Time entry with a number pad, the way Ivy Wallet takes an amount. Typing 1, 3, 2, 0 gives
 * 13:20. The set button stays disabled, and says why, while the digits are not a real time.
 */
@Composable
fun TimeKeypadDialog(
    title: String,
    initial: LocalTime,
    now: LocalTime,
    onDismiss: () -> Unit,
    onSet: (LocalTime) -> Unit,
) {
    // Nothing is typed yet, so the display starts on the time being edited.
    var digits by remember { mutableStateOf("") }
    val shown = digits.ifEmpty { "%02d%02d".format(initial.hour, initial.minute) }
        .padStart(4, '0').takeLast(4)
    val time = digitsToTime(shown)
    val hoursActive = digits.length <= 2

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(IvyShapes.Card)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DigitPair(shown.substring(0, 2), active = hoursActive && digits.isNotEmpty())
                Text(
                    ":",
                    style = MaterialTheme.typography.displayMedium,
                    color = IvyTheme.extra.slate,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                DigitPair(shown.substring(2), active = !hoursActive)
            }

            Text(
                text = if (time == null) {
                    "Hours go up to 23 and minutes up to 59"
                } else {
                    "Type the time. The newest digit goes on the right"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (time == null) MaterialTheme.colorScheme.error else IvyTheme.extra.slate,
                modifier = Modifier.fillMaxWidth(),
            )

            Pad(
                onDigit = { digit -> digits = (digits + digit).takeLast(4) },
                onBackspace = { digits = digits.dropLast(1) },
                onNow = { digits = "%02d%02d".format(now.hour, now.minute) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Cancel", onDismiss, Modifier.weight(1f))
                PrimaryButton(
                    text = "Set time",
                    onClick = { time?.let(onSet) },
                    enabled = time != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DigitPair(text: String, active: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.displayLarge,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Pad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onNow: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    Key(Modifier.weight(1f), onClick = { onDigit(digit) }) {
                        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(Modifier.weight(1f), onClick = onNow) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelLarge,
                    color = IvyTheme.extra.slate,
                )
            }
            Key(Modifier.weight(1f), onClick = { onDigit('0') }) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }
            Key(Modifier.weight(1f), onClick = onBackspace) {
                Icon(
                    Icons.Rounded.Backspace,
                    contentDescription = "Delete a digit",
                    tint = IvyTheme.extra.slate,
                )
            }
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(54.dp)
            .clip(IvyShapes.Field)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
