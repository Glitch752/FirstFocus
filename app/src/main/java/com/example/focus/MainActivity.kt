package com.example.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.focus.focus.FocusSessionNotificationManager
import com.example.focus.ui.theme.FocusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FocusSessionNotificationManager.createChannels(this)
        setContent {
            FocusTheme {
                FocusApp(
                    openFocusOnStart = intent.getBooleanExtra(EXTRA_OPEN_FOCUS, false)
                )
            }
        }
    }

    companion object {
        /** An intent extra used to indicate we should open to the focus page, used when opening the app from a notification */
        const val EXTRA_OPEN_FOCUS = "open_focus"
    }
}
