package app.enlily.focus.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.enlily.focus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives a broadcast to cancel an allowance for an app */
class CancelAllowanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FocusSessionNotificationManager.ACTION_CANCEL_ALLOWANCE) return
        val packageName = intent.getStringExtra(FocusSessionNotificationManager.EXTRA_ALLOWANCE_PACKAGE)
            ?.takeIf { it.isNotBlank() } ?: return
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.create(context).appDao().removeAllowance(packageName)
                FocusSessionNotificationManager(context).cancelAllowance()
                
                // send another intent action so we can update the UI if the app is still open (it probably is)
                context.sendBroadcast(
                    Intent(FocusSessionNotificationManager.ACTION_ALLOWANCE_CANCELLED)
                        .setPackage(context.packageName)
                        .putExtra(FocusSessionNotificationManager.EXTRA_ALLOWANCE_PACKAGE, packageName)
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
