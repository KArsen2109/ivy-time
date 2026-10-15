package com.ivy.time.day

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The day the Day screen is showing. It lives outside the view-model so the Week screen
 * can hand a date over when one of its columns is tapped.
 */
@Singleton
class SelectedDay @Inject constructor(clock: Clock) {
    private val state = MutableStateFlow(LocalDate.now(clock))

    val date: StateFlow<LocalDate> = state

    fun set(date: LocalDate) {
        state.value = date
    }

    fun shift(days: Long) {
        state.value = state.value.plusDays(days)
    }
}
