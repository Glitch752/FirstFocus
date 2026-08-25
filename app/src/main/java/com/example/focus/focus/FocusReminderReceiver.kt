package com.example.focus.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Broadcast receiver for focus reminder notifications */
class FocusReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(FocusReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = AppDatabase.create(appContext).appDao().focusReminders().firstOrNull { it.id == reminderId }
                if (reminder != null && reminder.enabled) {
                    // send it, obviously
                    FocusSessionNotificationManager(appContext).showReminder(reminder.title)
                    // schedule the next occurrence of this reminder
                    FocusReminderScheduler(appContext).reschedule(AppDatabase.create(appContext).appDao().focusReminders())
                }
            } finally {
                result.finish()
            }
        }
    }
}
