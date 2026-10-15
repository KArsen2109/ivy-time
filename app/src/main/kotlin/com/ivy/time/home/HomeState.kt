package com.ivy.time.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ivy.time.domain.usecase.PeriodType
import com.ivy.time.ui.PickerItem
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class HomeState(
    val periodLabel: String,
    val periodType: PeriodType,
    val periodTotal: String,
    val running: RunningUi?,
    val quickCategories: ImmutableList<PickerItem>,
    val days: ImmutableList<DayUi>,
    val search: String,
    val error: String?,
)

@Immutable
data class RunningUi(val elapsed: String, val title: String, val subtitle: String)

@Immutable
data class DayUi(val title: String, val total: String, val entries: ImmutableList<EntryUi>)

@Immutable
data class EntryUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val range: String,
    val duration: String,
    val color: Color,
    val icon: String?,
    val running: Boolean,
)

sealed interface HomeEvent {
    data class QuickStart(val categoryId: Long) : HomeEvent
    data object Stop : HomeEvent
    data class Search(val query: String) : HomeEvent
    data class SelectPeriod(val type: PeriodType) : HomeEvent
    data class ShiftPeriod(val steps: Long) : HomeEvent
    data class Delete(val entryId: Long) : HomeEvent
    data object DismissError : HomeEvent
}
