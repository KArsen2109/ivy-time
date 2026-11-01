package com.zham.app.quick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.toArgb
import com.zham.app.MainActivity
import com.zham.app.R
import com.zham.app.reminders.ReminderReceiver
import com.zham.design.system.ReminderColors
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one ongoing notification while a timer runs: the mode, a live clock counting from the real
 * start, and a Stop button. It is the phone's own chronometer, so nothing has to run in the background
 * to keep it ticking, and it reappears after a restart because the timer itself lives in the database.
 */
@Singleton
class TimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            runCatching {
                combine(
                    timeRepository.observeEntries().map { list -> list.firstOrNull { it.isRunning } },
                    categoryRepository.observeCategories(),
                ) { running, categories -> running to categories }
                    .distinctUntilChanged()
                    .collect { (running, categories) ->
                        runCatching { render(running, categories.associateBy { it.id }) }
                    }
            }
        }
    }

    private fun render(running: TimeEntry?, categories: Map<Long, com.zham.domain.model.Category>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (running == null) {
            manager.cancel(ID)
            return
        }
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows the mode you are tracking, with a Stop button"
                }
            )
        }
        val category = categories[running.categoryId]
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_STOP_TIMER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(running.note.ifBlank { category?.name ?: "Tracking" })
            .setContentText(category?.name ?: "Timer running")
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(running.start.toEpochMilli())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(category?.let { androidx.compose.ui.graphics.Color(it.color).toArgb() } ?: ReminderColors[0].toArgb())
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), "Stop", stop).build()
            )
            .build()
        manager.notify(ID, notification)
    }

    private companion object {
        const val CHANNEL = "running-timer"
        const val ID = 7002
    }
}
