package com.example.focus.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.focus.MainActivity
import com.example.focus.R
import com.example.focus.data.local.FocusSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Manages notifications for the active focus session and for a distracting app in the foreground */
class FocusSessionNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notificationScope = CoroutineScope(Dispatchers.Main.immediate)
    private val countdownJobs = mutableMapOf<Int, Job>()

    /** Intent that opens the app on the focus screen */
    private fun focusIntent(requestCode: Int = 0) = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_FOCUS, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun baseNotification(channel: String, title: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            // todo: get the color from the theme maybe?
            .setColor(0xFF6200EE.toInt())
            .setContentTitle(title)
            .setContentIntent(focusIntent())
            .setAutoCancel(true)

    /**
     * https://developer.android.com/develop/ui/compose/notifications/live-update
     * A live update notification showing a countdown. Promoted notifications are only available on Android 16+
     * and can be extremely finicky - some OEMs don't support them at all, some require users to follow specific
     * paths to enable them, and some have whitelists. We do our best to get permission for them, but if we can't,
     * this falls back to a normal notification, so it's not too bad.
     */
    private fun liveUpdateCountdownNotification(remaining: Long, title: String) =
        baseNotification(CHANNEL_SESSION_STATUS, title)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentText(formatRemaining(remaining))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remaining)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })

    private fun activeSessionNotification(remaining: Long) = liveUpdateCountdownNotification(remaining, "Focus session active").build()
    private fun allowanceNotification(remaining: Long, packageLabel: String) =
        liveUpdateCountdownNotification(remaining, "Focus allowance")
        .setContentText("$packageLabel allowed · ${formatRemaining(remaining)} remaining").build()

    /**  Show the notification for the active focus session, with a countdown timer */
    fun showActiveSession(session: FocusSessionEntity) {
        showCountdown(ACTIVE_SESSION_ID, session.startedAtMillis + session.plannedDurationMillis) { remaining ->
            activeSessionNotification(remaining)
        }
    }
    /** Cancel the notification for the active focus session */
    fun cancelActiveSession() {
        cancelCountdown(ACTIVE_SESSION_ID)
        manager.cancel(ACTIVE_SESSION_ID)
    }

    /** Show the notification for a temporary allowance for a distracting app */
    fun showAllowance(packageLabel: String, expiresAtMillis: Long) {
        showCountdown(ALLOWANCE_ID, expiresAtMillis) { remaining -> allowanceNotification(remaining, packageLabel) }
    }
    /** Cancel the notification for a temporary allowance for a distracting app */
    fun cancelAllowance() {
        cancelCountdown(ALLOWANCE_ID)
        manager.cancel(ALLOWANCE_ID)
    }

    /** Show the notification for a focus session reminder */
    fun showReminder(title: String) {
        manager.notify(
            REMINDER_ID,
            baseNotification(CHANNEL_SESSION_REMINDERS, title)
                .setContentText("Time for a focus session")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        )
    }

    private fun showCountdown(id: Int, endAtMillis: Long, notification: (remainingMillis: Long) -> android.app.Notification) {
        cancelCountdown(id)
        countdownJobs[id] = notificationScope.launch {
            while (isActive) {
                val remaining = (endAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                notify(id, notification(remaining))
                if (remaining == 0L) break
                delay(1_000L.milliseconds)
            }
            if (isActive) manager.cancel(id)
            countdownJobs.remove(id)
        }
    }
    private fun cancelCountdown(id: Int) {
        countdownJobs.remove(id)?.cancel()
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
    private fun notify(id: Int, notification: android.app.Notification) {
        val channel = manager.getNotificationChannel(CHANNEL_SESSION_STATUS)
        if (!manager.areNotificationsEnabled()) Log.w("FocusAccessibilityService", "Notification suppressed: app notifications are disabled (id=$id)")
        if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) return
        manager.notify(id, notification)
    }

    companion object {
        /** Channel id for focus session reminders */
        const val CHANNEL_SESSION_REMINDERS = "focus_session_reminders"
        /** Channel id for focus session status and distracting app notifications */
        const val CHANNEL_SESSION_STATUS = "focus_session_status"

        /** Notification id for the active focus session notification */
        private const val ACTIVE_SESSION_ID = 1001
        /** Notification id for the temporary allowance notification */
        private const val ALLOWANCE_ID = 1002
        /** Notification id for focus reminder notifications */
        private const val REMINDER_ID = 1003

        /** Creates the notification channels for focus session reminders and status. Should be called once on app startup. */
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_SESSION_REMINDERS, "Focus-session reminders", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SESSION_STATUS, "Focus-session status", NotificationManager.IMPORTANCE_DEFAULT)
            ))
        }
    }
}