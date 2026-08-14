package com.example.focus.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.local.TemporaryAllowanceEntity
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import com.example.focus.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val usageRepository by lazy { UsageStatsRepository(applicationContext) }
    private val appDao by lazy { AppDatabase.create(applicationContext).appDao() }

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
        Log.d("FocusAccessibilityService", "received window state changed event from package ${event.packageName}")

        if(event.packageName == applicationContext.packageName) return
        // systemui can be focused from notifications and other things, causing unnecessary flickering
        if(event.packageName == "com.android.systemui") return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == currentPackage) return

        // check if the app is now focused or is doing weird things like showing pop outs
        // if event.className is in the android.view package, we probably shouldn't mess with this
        // this is SO hacky, but it fixes issues with e.g. youtube sending another event when closed
        // also, yes, == true because it's optional. yay kotlin.
        if (event.className?.toString()?.startsWith("android.view") == true) return

        currentPackage = packageName
        if (packageName in selectedPackages) {
            scope.launch {
                val now = System.currentTimeMillis()
                if (appDao.activeAllowance(packageName, now) != null) return@launch

                // Keep DataStore, UsageStatsManager, and package-manager work off the
                // main thread, then only touch the WindowManager on the main thread.
                val settings = applicationContext.focusDataStore.data.first()
                val selected = selectedPackages
                val usage = usageRepository.today(selected).byPackage[packageName] ?: 0L
                val label = packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()

                // show() needs to run on the main thread
                withContext(Dispatchers.Main) {
                    overlayController.show(
                        packageName = packageName,
                        appLabel = label,
                        usageMillis = usage,
                        countdownSeconds = settings[SettingsKeys.preOpenCountdownSeconds] ?: 3,
                        onClose = {
                            overlayController.remove(clearDismissal = true)
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        },
                        onContinue = { durationMillis ->
                            scope.launch {
                                appDao.upsertAllowance(TemporaryAllowanceEntity(
                                    packageName = packageName,
                                    expiresAtMillis = System.currentTimeMillis() + durationMillis
                                ))
                            }
                        }
                    )
                }
            }
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