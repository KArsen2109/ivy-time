package com.ivy.time.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import com.ivy.design.system.colors.IvyColors
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant

/** The palette users pick project colors from. */
val ProjectColors: List<Color> = listOf(
    IvyColors.Purple, IvyColors.Blue, IvyColors.Green, IvyColors.Yellow,
    IvyColors.Orange, IvyColors.Red, IvyColors.Pink,
).map { it.primary }

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
