package com.ivy.time.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.ivy.time.domain.model.TimeError
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant

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
    TimeError.NonPositiveDuration -> "The block needs to last longer than zero."
    TimeError.NoWeekdaysChosen -> "Pick at least one weekday."
    TimeError.PlanNotFound -> "That block is gone."
}
