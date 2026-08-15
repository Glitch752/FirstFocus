package com.example.focus.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.focus.data.local.AppDatabase
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import com.example.focus.usage.UsageStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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
    private val allowanceRepository by lazy { AllowanceRepository(AppDatabase.create(applicationContext).appDao()) }
    /** A job that waits for the current allowance to expire, if any */
    private var allowanceExpirationJob: Job? = null
    /** A separate coroutine scope for the allowance expiration job, so we can cancel it without cancelling the main scope */
    private val policyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            showPrompt(packageName)
        } else {
            allowanceExpirationJob?.cancel()
            overlayController.remove(clearDismissal = true)
        }
    }

    /**
     * Show the overlay focus check prompt for the given package name, unless there is an active allowance
     * @param packageName The package name of the app to show the prompt for
     * @param ignoreAllowance If true, show the prompt even if there is an active allowance
     */
    private fun showPrompt(packageName: String, ignoreAllowance: Boolean = false) {
        // Keep data access off the main thread; only create/update the overlay on it
        scope.launch {
            val now = System.currentTimeMillis()
            if (!ignoreAllowance && allowanceRepository.hasActiveAllowance(packageName, now)) {
                val expiresAt = allowanceRepository.getAllowanceExpiration(packageName, now)
                if (expiresAt != null) restartExpirationJob(expiresAt, packageName)
                return@launch
            }

            val settings = applicationContext.focusDataStore.data.first()
            val selected = selectedPackages
            val usage = usageRepository.today(selected).byPackage[packageName] ?: 0L
            val label = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()

            withContext(Dispatchers.Main) {
                // Continue dismisses this package until the foreground package changes, so we need to clear it
                if (ignoreAllowance) overlayController.clearDismissal()

                overlayController.show(
                    packageName = packageName,
                    appLabel = label,
                    usageMillis = usage,
                    countdownSeconds = settings[SettingsKeys.preOpenCountdownSeconds] ?: 3,
                    onClose = {
                        allowanceExpirationJob?.cancel()
                        overlayController.remove(clearDismissal = true)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    },
                    onContinue = { durationMillis ->
                        // holy back-and-forth between threads omg
                        scope.launch {
                            val expiresAt = allowanceRepository.grantAllowance(packageName, durationMillis)
                            restartExpirationJob(expiresAt, packageName)
                        }
                    }
                )
            }
        }
    }

    /** restart the expiration job to wait the remaining duration and show the prompt again if still on the target package */
    private fun restartExpirationJob(expiresAt: Long, packageName: String) {
        allowanceExpirationJob?.cancel()
        allowanceExpirationJob = policyScope.launch {
            delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L).milliseconds)
            if (currentPackage == packageName && packageName in selectedPackages) {
                showPrompt(packageName, ignoreAllowance = true)
            }
        }
    }

    override fun onInterrupt() {
        if (::overlayController.isInitialized) overlayController.remove()
    }

    override fun onDestroy() {
        allowanceExpirationJob?.cancel()
        policyScope.cancel()
        scope.cancel()
        if (::overlayController.isInitialized) overlayController.remove()
        super.onDestroy()
    }
}