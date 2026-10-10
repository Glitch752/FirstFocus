package app.enlily.focus.accessibility

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.enlily.focus.ui.theme.FocusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class BlockingOverlayController(private val context: Context) {
    /** The overlay view, if currently shown */
    private var overlay: View? = null
    /** The package name of the app that was dismissed, if any */
    private var dismissedPackage: String? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null

    /** Custom recomposer used for the compose view */
    private var recomposer: Recomposer? = null
    /** The job used by the [recomposer] */
    private var recomposerJob: Job? = null
    private var windowManager: WindowManager? = null
    private var componentCallbacks: ComponentCallbacks? = null

    /** Show an overlay window with the given content in a compose view */
    private fun showOverlay(content: @Composable () -> Unit) {
        if (overlay != null) return

        // see DirectFrameClock, but we need to create a custom recomposer since using the default one causes
        // the UI to freeze when the overlay is shown and dismissed multiple times.
        val job = Job()
        val newRecomposer = Recomposer(Dispatchers.Main + job)
        CoroutineScope(Dispatchers.Main + job + DirectFrameClock).launch {
            newRecomposer.runRecomposeAndApplyChanges()
        }

        // using a compose view for the overlay is kind of annoying
        // We'll get a `ViewTreeLifecycleOwner not found from ComposeView` if we don't set the lifecycle owner here.
        val lifecycleOwner = OverlayLifecycleOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            // apply the custom recomposer
            compositionContext = newRecomposer
        }

        val wm = context.getSystemService(WindowManager::class.java)
        wm.addView(composeView, createOverlayLayoutParams(wm))
        windowManager = wm
        
        overlay = composeView
        componentCallbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                updateOverlayLayout()
            }
            
            // i guess...
            @Deprecated("...", replaceWith = ReplaceWith("Unit"))
            override fun onLowMemory() = Unit
        }.also(context::registerComponentCallbacks)

        overlayLifecycle = lifecycleOwner
        recomposer = newRecomposer
        recomposerJob = job

        composeView.setContent { FocusTheme { content() } }
    }

    /** Show the focus check overlay for the given package name, if not already shown or dismissed */
    fun showFocusCheck(
        packageName: String,
        appLabel: String,
        usageMillis: Long,
        countdownSeconds: Int,
        onClose: () -> Unit,
        onContinue: (Long) -> Unit
    ) {
        if (dismissedPackage == packageName) return

        showOverlay {
            FocusCheckOverlay(appLabel, usageMillis, countdownSeconds, onClose) { durationMillis ->
                dismissedPackage = packageName
                remove()
                onContinue(durationMillis)
            }
        }
    }

    /** Show the focus blocking overlay for the given package name, if not already shown */
    fun showFocusBlock(appLabel: String, startedAtMillis: Long, durationMillis: Long, onClose: () -> Unit) {
        showOverlay {
            FocusBlockingOverlay(appLabel, startedAtMillis, durationMillis, onClose)
        }
    }

    /** Unfortunately, we need to hack around covering the nav bar in api 30+: https://stackoverflow.com/a/79501942 */
    private fun createOverlayLayoutParams(windowManager: WindowManager): WindowManager.LayoutParams {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        // this is reeeally hacky but... whatever
        val isLandscape = metrics.bounds.width() > metrics.bounds.height()
        val topInset = if (isLandscape) 0 else insets.top
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            metrics.bounds.height() - insets.bottom - topInset,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.BOTTOM
            y = insets.bottom
        }
    }

    private fun updateOverlayLayout() {
        val view = overlay ?: return
        val wm = windowManager ?: return
        wm.updateViewLayout(view, createOverlayLayoutParams(wm))
    }

    /** Clear the dismissed package, allowing the overlay to be shown again for the same app */
    fun clearDismissal() {
        dismissedPackage = null
    }

    /** Remove the overlay, if currently shown */
    fun remove(clearDismissal: Boolean = false) {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        
        componentCallbacks?.let(context::unregisterComponentCallbacks)
        componentCallbacks = null
        windowManager = null

        // Discard and cancel the recomposer
        recomposer?.cancel()
        recomposerJob?.cancel()
        recomposer = null
        recomposerJob = null

        overlayLifecycle?.destroy()
        overlayLifecycle = null
        if (clearDismissal) this.clearDismissal()
    }
}

/**
 * A frame clock that uses the Choreographer to provide frame timing.
 * Truthfully, I don't understand why this fixes recomposition tracking issues, but without it, some sort of
 * thread-global state with [androidx.compose.ui.platform.AndroidUiDispatcher.CurrentThread] gets messed up
 * and causes the ui to freeze.
 */
private object DirectFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        suspendCancellableCoroutine { cont ->
            Choreographer.getInstance().postFrameCallback { t ->
                cont.resumeWith(runCatching { onFrame(t) })
            }
        }
}

/**
 * Mock lifecycle owner for the overlay, since it doesn't have a real lifecycle but needs one for compose to work properly
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
