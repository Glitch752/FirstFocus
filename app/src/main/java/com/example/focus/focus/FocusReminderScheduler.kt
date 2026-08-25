package com.example.focus.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.focus.data.local.FocusReminderEntity
import java.util.Calendar

/** Schedules the next occurrence of enabled focus reminders */
class FocusReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedules the next occurrence of all enabled reminders, cancelling any existing alarms */
    fun reschedule(reminders: List<FocusReminderEntity>) {
        reminders.forEach { reminder -> cancel(reminder.id) }
        reminders.filter { it.enabled }.forEach { reminder ->
            val next = nextOccurrence(reminder)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(reminder.id))
        }
    }

    /** Cancels any existing alarm for the given reminder ID */
    fun cancel(reminderId: Long) {
        alarmManager.cancel(pendingIntent(reminderId))
    }

    /** Returns the next occurrence of the given reminder in milliseconds since epoch */
    private fun nextOccurrence(reminder: FocusReminderEntity): Long {
        val now = Calendar.getInstance()
        for (offset in 0..7) {
            val candidate = now.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            candidate.set(Calendar.HOUR_OF_DAY, reminder.hour)
            candidate.set(Calendar.MINUTE, reminder.minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
            if (candidate.timeInMillis > now.timeInMillis &&
                reminder.daysOfWeek and (1 shl (candidate.get(Calendar.DAY_OF_WEEK) - 1)) != 0) {
                return candidate.timeInMillis
            }
        }
        return now.timeInMillis + 24 * 60 * 60 * 1000L
    }

    /** Returns a PendingIntent for the given reminder ID, which will trigger the FocusReminderReceiver */
    private fun pendingIntent(reminderId: Long) = PendingIntent.getBroadcast(
        context,
        reminderId.toInt(),
        Intent(context, FocusReminderReceiver::class.java).putExtra(EXTRA_REMINDER_ID, reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /** Extra key for passing the reminder ID to the FocusReminderReceiver */
        const val EXTRA_REMINDER_ID = "focus_reminder_id"
    }
}
