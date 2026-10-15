package com.zham.app.day

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

    /** True while the user has not moved away from today, so a new day should carry them along. */
    private var followingToday = true
    private var lastToday = LocalDate.now(clock)

    val date: StateFlow<LocalDate> = state

    fun set(date: LocalDate) {
        state.value = date
        followingToday = date == lastToday
    }

    fun shift(days: Long) = set(state.value.plusDays(days))

    /** Called when the calendar day rolls over, e.g. the app stayed open past midnight. */
    fun dayChanged(today: LocalDate) {
        if (today == lastToday) return
        lastToday = today
        if (followingToday) state.value = today
    }
}
