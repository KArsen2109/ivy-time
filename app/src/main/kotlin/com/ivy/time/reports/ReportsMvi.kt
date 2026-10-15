package com.ivy.time.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.formatShort
import com.ivy.time.domain.usecase.startInstant
import com.ivy.time.domain.usecase.summarize
import com.ivy.time.ui.ComposeViewModel
import com.ivy.time.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ReportPeriod { Week, Month }

@Immutable
data class ReportsState(
    val period: ReportPeriod,
    val total: String,
    val rows: ImmutableList<ReportRow>,
)

/** [fraction] is the share of the biggest row, for the progress bar. */
@Immutable
data class ReportRow(val name: String, val color: Color?, val duration: String, val fraction: Float)

sealed interface ReportsEvent {
    data class SelectPeriod(val period: ReportPeriod) : ReportsEvent
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val timeRepository: TimeRepository,
    private val clock: Clock,
) : ComposeViewModel<ReportsState, ReportsEvent>() {

    private val period = mutableStateOf(ReportPeriod.Week)

    @Composable
    override fun uiState(): ReportsState {
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val today = LocalDate.now(clock)
        val firstDay = when (period.value) {
            ReportPeriod.Week -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ReportPeriod.Month -> today.withDayOfMonth(1)
        }
        val lastDayExclusive = when (period.value) {
            ReportPeriod.Week -> firstDay.plusWeeks(1)
            ReportPeriod.Month -> firstDay.plusMonths(1)
        }
        val totals = summarize(
            entries = entries,
            from = firstDay.startInstant(clock.zone),
            to = lastDayExclusive.startInstant(clock.zone),
            now = now,
        )
        val byId = projects.associateBy { it.id }
        val max = totals.maxOfOrNull { it.total.toMillis() }?.coerceAtLeast(1) ?: 1

        return ReportsState(
            period = period.value,
            total = totals.fold(Duration.ZERO) { acc, it -> acc + it.total }.formatShort(),
            rows = totals.map { row ->
                val project = byId[row.projectId]
                ReportRow(
                    name = project?.name ?: "No project",
                    color = project?.let { Color(it.color) },
                    duration = row.total.formatShort(),
                    fraction = row.total.toMillis().toFloat() / max,
                )
            }.toImmutableList(),
        )
    }

    override fun onEvent(event: ReportsEvent) {
        when (event) {
            is ReportsEvent.SelectPeriod -> period.value = event.period
        }
    }
}
