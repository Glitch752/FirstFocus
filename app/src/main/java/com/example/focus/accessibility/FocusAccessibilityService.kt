package com.example.focus.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.focus.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest

@SuppressLint("AccessibilityPolicy")
class FocusAccessibilityService : AccessibilityService() {
    /** We need a scope to launch coroutines since it doens't have a lifecycle like an activity or fragment */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Controller for showing the overlay prompt */
    private lateinit var overlayController: BlockingOverlayController
    /** The package name of the app that is currently in the foreground */
    private var currentPackage: String? = null
    /** The set of package names that are selected as distracting */
    private var selectedPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        overlayController = BlockingOverlayController(this)
        scope.launch {
            AppDatabase.create(applicationContext).appDao().observeSelectedApps().collectLatest { apps ->
                selectedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        Log.d("FocusAccessibilityService", "received window state changed event from package ${event?.packageName}")

        if(event.packageName == applicationContext.packageName) return
        // systemui can be focused from notifications and other things, causing unnecessary flickering
        if(event.packageName == "com.android.systemui") return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == currentPackage) return
        currentPackage = packageName
        if (packageName in selectedPackages) {
            overlayController.show(packageName)
        } else {
            overlayController.remove(clearDismissal = true)
        }
    }

    override fun onInterrupt() {
        if (::overlayController.isInitialized) overlayController.remove()
    }

    override fun onDestroy() {
        scope.cancel()
        if (::overlayController.isInitialized) overlayController.remove()
        super.onDestroy()
    }
}