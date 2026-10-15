package com.zham.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import com.zham.app.MainActivity
import com.zham.app.R
import com.zham.app.ui.TimeFormatters
import com.zham.data.SettingsStore
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.PlanRepository
import com.zham.domain.usecase.Reminder
import com.zham.domain.usecase.nextReminder
import com.zham.domain.usecase.remindersBetween
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows plan reminders and keeps one alarm set for the next one.
 *
 * [sync] is safe to call whenever something may have changed (plans edited, phone restarted,
 * clock or zone changed, the alarm went off): it shows what is due and sets the next alarm.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planRepository: PlanRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
) {
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        createChannel()
        val now = clock.instant()
        val plans = planRepository.observePlans().firstOrNull().orEmpty()
        val completed = planRepository.observeCompletions().firstOrNull().orEmpty().toSet()

        // The first run has no past to catch up on.
        val handledUpTo = settingsStore.lastReminderMillis()?.let(Instant::ofEpochMilli) ?: now
        val zone = clock.zone

        // A reminder that arrives late (a sleeping phone, a restart) is still worth showing while its
        // block is under way; once the block is over it no longer helps.
        val due = remindersBetween(plans, completed, handledUpTo, now, zone)
            .filter { it.occurrence.endsAt.isAfter(now) }
        if (due.isNotEmpty()) {
            val categories = categoryRepository.observeCategories().firstOrNull().orEmpty()
                .associateBy { it.id }
            val formatters = TimeFormatters(zone, settingsStore.settings.firstOrNull()?.use24Hours ?: true)
            due.forEach { show(it, now, formatters, categories[it.occurrence.plan.categoryId]?.name) }
        }
        settingsStore.setLastReminderMillis(now.toEpochMilli())

        schedule(nextReminder(plans, completed, now, zone)?.triggerAt)
    }

    private fun show(reminder: Reminder, now: Instant, formatters: TimeFormatters, category: String?) {
        val plan = reminder.occurrence.plan
        val minutes = Duration.between(now, reminder.occurrence.startsAt).toMinutes()
        val whenText = when {
            minutes < 0 -> "started ${-minutes} min ago"
            minutes == 0L -> "starts now"
            minutes < 60 -> "starts in $minutes min"
            else -> "starts in ${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else ""
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(plan.note.ifBlank { category ?: "Planned block" })
            .setContentText(formatters.time(plan.start) + " · " + whenText)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(NotificationManager::class.java)
        // Without the notification permission the system simply drops this.
        val id = (plan.id * 40_000 + reminder.occurrence.date.toEpochDay()).hashCode()
        runCatching { manager.notify(id, notification) }
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Plan reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders before the blocks you planned"
                }
            )
        }
    }

    private fun schedule(triggerAt: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context, 1, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (triggerAt == null) {
            alarms.cancel(intent)
            return
        }
        val millis = triggerAt.toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
            }
        }.onFailure {
            // Exact alarms were revoked between the check and the call; a looser alarm still works.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent) }
        }
    }

    private companion object {
        const val CHANNEL = "plan-reminders"
    }
}
