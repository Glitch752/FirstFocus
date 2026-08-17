package com.example.focus.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Manages the scheduling of alarms for focus sessions.
 * Alarms are used to automatically end a focus session when the time expires, even if the app is closed
 * or the device is restarted.
 */
class FocusSessionAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(sessionId: Long, expiresAtMillis: Long) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiresAtMillis, pendingIntent(sessionId))
    }

    fun cancel(sessionId: Long) {
        alarmManager.cancel(pendingIntent(sessionId))
    }

    /** Get a PendingIntent that will trigger the FocusSessionAlarmReceiver when the alarm goes off. */
    private fun pendingIntent(sessionId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId.toInt(),
        Intent(context, FocusSessionAlarmReceiver::class.java)
            .putExtra(EXTRA_SESSION_ID, sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /** The key for the extra in the intent that contains the ID of the focus session. */
        const val EXTRA_SESSION_ID = "focus_session_id"
    }
}
