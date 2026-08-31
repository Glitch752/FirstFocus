package app.enlily.focus.usage

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schedules a daily update of usage stats at 12:01am so we store the previous day's usage stats in the database.
 * Without this, if the app wasn't opened for a few days, we'd lose old usage data
 */
class UsageRefreshScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleNext() {
        val tomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow, pendingIntent())
    }

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, UsageRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /** I couldn't find a standardized way to pick these so... 12345 */
        private const val REQUEST_CODE = 12345
    }
}
