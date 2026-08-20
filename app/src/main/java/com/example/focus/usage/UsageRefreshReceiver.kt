package com.example.focus.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class UsageRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val date = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
                val dao = AppDatabase.create(appContext).appDao()
                dao.clearDailyUsage(date.toString())
                UsageStatsRepository(appContext).usageForDate(date)
                    .takeIf { it.isNotEmpty() }?.let { dao.insertDailyUsage(it) }
                UsageRefreshScheduler(appContext).scheduleNext()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
