package com.example.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.focus.data.local.AppDatabase
import com.example.focus.focus.FocusReminderScheduler
import com.example.focus.focus.FocusSessionNotificationManager
import com.example.focus.ui.theme.FocusTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    /** Requests delivered while this activity instance already exists */
    private val focusOpenRequests = Channel<Unit>(Channel.BUFFERED)
    private val focusOpenEvents = focusOpenRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FocusSessionNotificationManager.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch {
            FocusReminderScheduler(this@MainActivity)
                .reschedule(AppDatabase.create(this@MainActivity).appDao().focusReminders())
        }

        /** We keep the same compose tree and send notifications if opening an intent to the focus page */
        val openFocusOnStart = intent.getBooleanExtra(EXTRA_OPEN_FOCUS, false)
        setContent {
            FocusTheme {
                FocusApp(
                    openFocusOnStart = openFocusOnStart,
                    regenerateHistoryOnStart = intent.getBooleanExtra(EXTRA_REGENERATE_HISTORY, false),
                    openFocusRequests = focusOpenEvents
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_FOCUS, false)) {
            focusOpenRequests.trySend(Unit)
        }
    }

    companion object {
        /** An intent extra used to indicate we should open to the focus page, used when opening the app from a notification */
        const val EXTRA_OPEN_FOCUS = "open_focus"
        const val EXTRA_REGENERATE_HISTORY = "regenerate_history"
    }
}
