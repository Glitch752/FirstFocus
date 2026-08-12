package com.example.focus.permissions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

/** A permission that the app requires to function */
interface RequiredPermission {
    val title: String
    val description: String
    fun isGranted(context: Context): Boolean
    fun intent(): Intent
}

/** Permission for usage stats service */
object UsageAccessPermission : RequiredPermission {
    override val title = "Usage Access"
    override val description = "Allows measuring app usage for statistics"

    override fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        return manager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000, now).isNotEmpty()
    }

    override fun intent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}

/** Permission the accessibility service for app prompts/blocking */
object AccessibilityPermission : RequiredPermission {
    override val title = "Accessibility Access"
    override val description = "Allows us to show prompts over distracting apps."

    override fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        ).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    override fun intent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}

/** Returns a list of all required permissions */
fun requiredPermissions(): List<RequiredPermission> = listOf(
    UsageAccessPermission,
    AccessibilityPermission
)
