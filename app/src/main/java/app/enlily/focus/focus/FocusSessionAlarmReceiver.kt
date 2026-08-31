package app.enlily.focus.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.enlily.focus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the alarm broadcast when a focus session is supposed to end.
 * This is triggered by the FocusSessionAlarmScheduler when a focus session's planned duration expires.
 */
class FocusSessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(FocusSessionAlarmScheduler.EXTRA_SESSION_ID, -1L)
        if (sessionId < 0) return

        // keep the broadcast receiver alive while we modify the db in the background thread; gives us a few
        // seconds max, which is plenty
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.create(context.applicationContext).appDao()
                    .completeFocusSessionIfActive(sessionId, System.currentTimeMillis())
            } finally {
                result.finish()
            }
        }
    }
}
