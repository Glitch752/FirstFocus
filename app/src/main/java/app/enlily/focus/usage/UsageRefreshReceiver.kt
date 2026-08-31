package app.enlily.focus.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.enlily.focus.data.local.AppDatabase
import app.enlily.focus.data.local.DailyUsageCompleteness
import app.enlily.focus.data.local.DailyUsageStatusEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** A broadcast receiver for our usage stats refresh alarm. */
class UsageRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext

                // update yesterday's usage stats in the database
                val date = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
                val dao = AppDatabase.create(appContext).appDao()
                dao.clearDailyUsage(date.toString())
                val entries = UsageStatsRepository(appContext).usageForDate(date)
                entries.takeIf { it.isNotEmpty() }?.let { dao.insertDailyUsage(it) }
                
                // and the status for that day
                val selectedPackages = dao.selectedPackageNames().toSet()
                dao.upsertDailyUsageStatus(DailyUsageStatusEntity(
                    date.toString(), DailyUsageCompleteness.FULL, System.currentTimeMillis()
                ))

                // schedule another refresh for tomorrow
                UsageRefreshScheduler(appContext).scheduleNext()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
