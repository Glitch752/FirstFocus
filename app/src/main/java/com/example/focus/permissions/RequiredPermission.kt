package com.example.focus.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import com.example.focus.BuildConfig
import com.example.focus.accessibility.FocusAccessibilityService
import com.example.focus.data.settings.SettingsKeys
import com.example.focus.data.settings.focusDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The content of permission confirmation modals */
typealias ConfirmPermissionContent = @Composable (
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) -> Unit

/**
 * UI supplied by a permission before its request intent is launched.
 * Content callbacks allow the permission to provide its own buttons and decide when the request should be
 * launched or the modal should be dismissed
 */
class ConfirmPermissionModal(
    val content: ConfirmPermissionContent
)

/** A permission that the app requires to function */
interface RequiredPermission {
    val title: String
    val description: String
    suspend fun isGranted(context: Context): Boolean

    /** An optional modal displayed before running the intent to request the permission. */
    fun confirmModal(): ConfirmPermissionModal? = null
    fun intent(): Intent
}

/** Permission for usage stats service */
object UsageAccessPermission : RequiredPermission {
    override val title = "Usage access"
    override val description = "Allows measuring app usage for statistics"

    override suspend fun isGranted(context: Context): Boolean {
        // this is such a weird api what
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun intent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}

/** Permission the accessibility service for app prompts/blocking */
object AccessibilityPermission : RequiredPermission {
    override val title = "Accessibility access"
    override val description = "Allows us to show prompts over distracting apps."

    override suspend fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(context, FocusAccessibilityService::class.java)
        val enabledServices = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        val isEnabled = enabledServices.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
        }

        // Sometimes the accessibility service returns an incomplete service list? This allows us to double-check, though
        // I'm not sure how it's technically different
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val settingSaysEnabled = settingValue
            ?.split(':')
            ?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.contains(expected) == true
        if (settingSaysEnabled != isEnabled) {
            // fallback to true since we don't want to incorrectly show the banner
            return true
        }

        return isEnabled
    }

    override fun intent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

/** Permission for posting notifications */
object NotificationPermission : RequiredPermission {
    override val title = "Notification permissions"
    override val description = "Used to show notifications and live updates for focus sessions and reminders."

    override suspend fun isGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun intent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
    }
}

/** User-controlled permission for promoted notifications (Android 16+ Live Updates) */
object PromotedNotificationPermission : RequiredPermission {
    override val title = "Promoted notification permissions"
    override val description = "Allows active focus sessions to appear as a live countdown in the status bar"

    override suspend fun isGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 36 ||
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun intent() = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
    }
}

/** Samsung-specific permission for disabling live notification whitelist */
object SamsungLiveNotificationPermission : RequiredPermission {
    override val title = "Samsung live notification setting"
    override val description = "Samsung has specific requirements for live notifications in the status bar"

    override suspend fun isGranted(context: Context): Boolean {
        val oemNotificationPermissionDismissed = context.focusDataStore.data
            .map { it[SettingsKeys.oemNotificationPermissionDismissed] }
            .firstOrNull() ?: false
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        return Build.VERSION.SDK_INT < 36 || oemNotificationPermissionDismissed || !isSamsung
    }

    suspend fun setOemNotificationPermissionDismissed(context: Context, dismissed: Boolean) {
        context.focusDataStore.edit { settings ->
            settings[SettingsKeys.oemNotificationPermissionDismissed] = dismissed
        }
    }
    override fun confirmModal(): ConfirmPermissionModal {
        return ConfirmPermissionModal { onConfirm, onDismiss ->
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            AlertDialog(
                onDismissRequest = onDismiss, // don't store the dismissal if clicking off
                title = { Text("Live notifications") },
                text = {
                    Text("""Samsung currently requires a developer mode setting to be enabled for apps (that aren't specially approved by Samsung) to show live notifications (chips in the status bar).

If you really want this functionality, you can:
- Enable developer mode (if not already) by tapping on the build number in your phone's settings 7 times
- Open the developer options ("Continue" will take you there if developer mode is enabled)
- Scroll down to "More settings" at the bottom and click it
- Enable the setting "Live notifications for all apps" under "Apps"

You'll only see this screen once.""")
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { setOemNotificationPermissionDismissed(context, true) }
                        onConfirm()
                    }) { Text("Go to developer options") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch { setOemNotificationPermissionDismissed(context, true) }
                        onDismiss()
                    }) { Text("Dismiss permanently") }
                }
            )
        }
    }
    override fun intent() = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
    }
}

/** Returns a list of all required permissions */
fun requiredPermissions(): List<RequiredPermission> = listOf(
    UsageAccessPermission,
    AccessibilityPermission,
    NotificationPermission,
    PromotedNotificationPermission,
    SamsungLiveNotificationPermission
)
