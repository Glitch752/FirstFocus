package com.example.focus.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Repository for retrieving installed apps on the device
 */
class InstalledAppRepository(private val context: Context) {
    /**
     * Gets a list of all installed apps that can be launched from the home screen (have Intent.ACTION_MAIN and
     * Intent.CATEGORY_LAUNCHER), aren't ourselves, and are deduplicated by package name. Results are sorted by
     * name.
     */
    fun getLauncherApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(
                packageName = it.packageName,
                label = it.loadLabel(context.packageManager).toString(),
                icon = it.loadIcon(context.packageManager)
            ) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}