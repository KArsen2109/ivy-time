package com.ivy.time.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import com.ivy.design.system.colors.IvyColors
import com.ivy.time.domain.model.TimeError
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant

/** The palette categories and projects pick their color from. */
val IvyPalette: List<Color> = listOf(
    IvyColors.Purple, IvyColors.Blue, IvyColors.Green, IvyColors.Yellow,
    IvyColors.Orange, IvyColors.Red, IvyColors.Pink,
).flatMap { listOf(it.primary, it.kindaDark) }

/** Current time, refreshed every second while [active]. */
@Composable
fun tickingNow(clock: Clock, active: Boolean): State<Instant> =
    produceState(initialValue = clock.instant(), key1 = active) {
        value = clock.instant()
        while (active) {
            delay(1_000)
            value = clock.instant()
        }
    }

fun TimeError.message(): String = when (this) {
    TimeError.TimerAlreadyRunning -> "A timer is already running. Stop it first."
    TimeError.NoRunningTimer -> "No timer is running."
    TimeError.EndNotAfterStart -> "The end must be after the start."
    TimeError.EndInTheFuture -> "You cannot log time that has not passed yet."
    TimeError.BlankName -> "Enter a name."
    TimeError.NonPositiveDuration -> "Enter a duration greater than 0."
}
