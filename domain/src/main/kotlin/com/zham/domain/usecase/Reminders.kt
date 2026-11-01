package com.zham.domain.usecase

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanOccurrence
import com.zham.domain.model.occurrenceOn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class ReminderKind { BeforeStart, BeforeEnd }

/**
 * A notification to show at [triggerAt] for [occurrence]. [lead] is the minutes before the start (or
 * the end), and [colorIndex] tells which of the block's reminders this is, so the same one can be
 * drawn in the same colour everywhere.
 */
data class Reminder(
    val occurrence: PlanOccurrence,
    val triggerAt: Instant,
    val kind: ReminderKind = ReminderKind.BeforeStart,
    val lead: Int = 0,
    val colorIndex: Int = 0,
)

/** The longest lead a reminder can have: one day. */
const val MAX_REMINDER_MINUTES = 24 * 60

/** How far ahead [nextReminder] looks; enough for a monthly block. */
private val HORIZON: Duration = Duration.ofDays(62)

/** The reminders of one block as (kind, lead, colour index), starts first by lead, then the end reminder. */
fun PlanBlock.reminderSlots(): List<Triple<ReminderKind, Int, Int>> {
    val starts = reminders.distinct().sorted().mapIndexed { index, lead -> Triple(ReminderKind.BeforeStart, lead, index) }
    val end = endReminder?.takeIf { !point }?.let { Triple(ReminderKind.BeforeEnd, it, starts.size) }
    return starts + listOfNotNull(end)
}

/**
 * The reminders that fall in `(after, upTo]`, earliest first. Occurrences already ticked off
 * are skipped, and so are blocks without a reminder.
 */
fun remindersBetween(
    plans: List<PlanBlock>,
    completed: Set<PlanCompletion>,
    after: Instant,
    upTo: Instant,
    zone: ZoneId,
): List<Reminder> {
    if (!upTo.isAfter(after)) return emptyList()
    val reminding = plans.filter { it.reminders.isNotEmpty() || (it.endReminder != null && !it.point) }
    if (reminding.isEmpty()) return emptyList()

    // A reminder fires before its block starts or ends, never more than a day before.
    val firstDay = after.atZone(zone).toLocalDate().minusDays(1)
    val lastDay = upTo.atZone(zone).toLocalDate().plusDays(2)
    val found = mutableListOf<Reminder>()
    var day = firstDay
    while (!day.isAfter(lastDay)) {
        reminding.forEach { plan ->
            val occurrence = plan.occurrenceOn(day, zone, completed) ?: return@forEach
            if (occurrence.done) return@forEach
            plan.reminderSlots().forEach { (kind, lead, colorIndex) ->
                val from = if (kind == ReminderKind.BeforeStart) occurrence.startsAt else occurrence.endsAt
                val triggerAt = from.minus(Duration.ofMinutes(lead.coerceIn(0, MAX_REMINDER_MINUTES).toLong()))
                if (triggerAt.isAfter(after) && !triggerAt.isAfter(upTo)) {
                    found += Reminder(occurrence, triggerAt, kind, lead, colorIndex)
                }
            }
        }
        day = day.plusDays(1)
    }
    return found.sortedBy { it.triggerAt }
}

/** The first reminder after [after], or null when nothing is due in the next two months. */
fun nextReminder(
    plans: List<PlanBlock>,
    completed: Set<PlanCompletion>,
    after: Instant,
    zone: ZoneId,
): Reminder? = remindersBetween(plans, completed, after, after.plus(HORIZON), zone).firstOrNull()
