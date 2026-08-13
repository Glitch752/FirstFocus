package com.example.focus.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import com.example.focus.ui.theme.FocusTheme
import kotlin.time.Duration.Companion.milliseconds

class BlockingOverlayController(private val context: Context) {
    /** The overlay view, if currently shown */
    private var overlay: View? = null
    /** The package name of the app that was dismissed, if any */
    private var dismissedPackage: String? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    /** Show the overlay for the given package name, if not already shown or dismissed */
    fun show(packageName: String) {
        if (overlay != null || dismissedPackage == packageName) return

        val windowManager = context.getSystemService(WindowManager::class.java)
        
        // using a compose view for the overlay is kind of annoying
        // We'll get a `ViewTreeLifecycleOwner not found from ComposeView` if we don't
        // set the lifecycle owner here.
        val lifecycleOwner = OverlayLifecycleOwner()
        val content = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                FocusTheme {
                    PromptOverlay(packageName) {
                        dismissedPackage = packageName
                        remove()
                    }
                }
            }
        }

        // Unfortunately, we need to hack around covering the nav bar in api 30+: https://stackoverflow.com/a/79501942
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE
        ).apply {
            val metrics = windowManager.currentWindowMetrics

            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            gravity = Gravity.BOTTOM;
            y = insets.bottom;
            height = metrics.bounds.height() - insets.bottom - insets.top
        }
        windowManager.addView(content, params)
        overlay = content
        overlayLifecycle = lifecycleOwner
    }

    /** Clear the dismissed package, allowing the overlay to be shown again for the same app */
    fun clearDismissal() {
        dismissedPackage = null
    }

    /** Remove the overlay, if currently shown */
    fun remove(clearDismissal: Boolean = false) {
        overlay?.let { context.getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
        overlayLifecycle?.destroy()
        overlayLifecycle = null
        if (clearDismissal) this.clearDismissal()
    }
}

/**
 * Mock lifecycle owner for the overlay, since it doesn't have a real lifecycle but needs one
 * for compose to work properly
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val savedStateController = SavedStateRegistryController.create(this)
    private val registry = LifecycleRegistry(this)

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedStateController.savedStateRegistry

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
private fun PromptOverlay(packageName: String, onContinue: () -> Unit) {
    var secondsRemaining by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            kotlinx.coroutines.delay(1_000.milliseconds)
            secondsRemaining--
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("focus check!", fontSize = 24.sp)
            Text("You opened $packageName", modifier = Modifier.padding(top = 12.dp))
            Button(
                onClick = onContinue,
                enabled = secondsRemaining == 0,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(if (secondsRemaining == 0) "Continue" else "Continue ($secondsRemaining)")
            }
        }
    }
}