package com.example.focus.usage

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

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

    companion object { private const val REQUEST_CODE = 1207 }
}
