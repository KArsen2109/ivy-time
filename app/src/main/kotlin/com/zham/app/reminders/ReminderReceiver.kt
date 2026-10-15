package com.zham.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes the scheduler when the alarm goes off, and again after anything that can move or
 * lose alarms: a restart, an app update, or a change of clock or time zone.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun scheduler(): ReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = EntryPointAccessors
            .fromApplication(context.applicationContext, Access::class.java)
            .scheduler()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                scheduler.sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.zham.app.REMIND"
    }
}
