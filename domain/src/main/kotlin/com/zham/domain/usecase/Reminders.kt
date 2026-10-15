package com.zham.domain.usecase

import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanOccurrence
import com.zham.domain.model.occurrenceOn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A notification to show at [triggerAt] for [occurrence], which starts later. */
data class Reminder(val occurrence: PlanOccurrence, val triggerAt: Instant)

/** The longest lead a reminder can have: one day. */
const val MAX_REMINDER_MINUTES = 24 * 60

/** How far ahead [nextReminder] looks; enough for a monthly block. */
private val HORIZON: Duration = Duration.ofDays(62)

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
    val reminding = plans.filter { it.reminderMinutes != null }
    if (reminding.isEmpty()) return emptyList()

    // A reminder fires before its block starts, never more than a day before.
    val firstDay = after.atZone(zone).toLocalDate()
    val lastDay = upTo.atZone(zone).toLocalDate().plusDays(2)
    val found = mutableListOf<Reminder>()
    var day = firstDay
    while (!day.isAfter(lastDay)) {
        reminding.forEach { plan ->
            val occurrence = plan.occurrenceOn(day, zone, completed) ?: return@forEach
            if (occurrence.done) return@forEach
            val lead = plan.reminderMinutes!!.coerceIn(0, MAX_REMINDER_MINUTES)
            val triggerAt = occurrence.startsAt.minus(Duration.ofMinutes(lead.toLong()))
            if (triggerAt.isAfter(after) && !triggerAt.isAfter(upTo)) {
                found += Reminder(occurrence, triggerAt)
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
