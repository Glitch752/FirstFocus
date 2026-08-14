package com.example.focus.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.focus.ui.theme.FocusTheme

class BlockingOverlayController(private val context: Context) {
    /** The overlay view, if currently shown */
    private var overlay: View? = null
    /** The package name of the app that was dismissed, if any */
    private var dismissedPackage: String? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    /** Show the overlay for the given package name, if not already shown or dismissed */
    fun show(
        packageName: String,
        appLabel: String,
        usageMillis: Long,
        countdownSeconds: Int,
        onClose: () -> Unit
    ) {
        if (overlay != null || dismissedPackage == packageName) return

        val windowManager = context.getSystemService(WindowManager::class.java)

        // using a compose view for the overlay is kind of annoying
        // We'll get a `ViewTreeLifecycleOwner not found from ComposeView` if we don't
        // set the lifecycle owner here.
        val lifecycleOwner = OverlayLifecycleOwner()
        val content = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
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

        // compose after the view has been attached to make sure animations work
        content.setContent {
            FocusTheme {
                FocusCheckOverlay(appLabel, usageMillis, countdownSeconds, onClose) {
                    dismissedPackage = packageName
                    remove()
                }
            }
        }
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
