package com.example.focus.grayscale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Controls Android's display grayscale mode. Android provides two independent ways to control greyscale mode:
 * - The daltonizer, which is intended for accessibility purposes and can be enabled through Settings.Secure. It:
 *     - Can be edited without persistent shell/adb access once we get the WRITE_SECURE_SETTINGS permission
 *     - Stays persistent across reboots
 *     - Only allows us to enable/disable and abruptly changes with no animation. Saturation is never applied for
 *       grayscale mode; see [ColorDisplayService's onAccessibilityDaltonizerChanged](https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/services/core/java/com/android/server/display/color/ColorDisplayService.java;l=798).
 *     - Enabled by setting `accessibility_display_daltonizer_enabled` to 1 and `accessibility_display_daltonizer` to 0 in Settings.Secure
 * - ColorDisplayService's [saturation level](https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/services/core/java/com/android/server/display/color/ColorDisplayService.java;l=2021),
 *   which can only be accessed by user applications through [ColorDisplayShellCommand](https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/services/core/java/com/android/server/display/color/ColorDisplayShellCommand.java). It:
 *     - Can only be accessed with an active shell/adb session, so it requires active Shizuku (or root technically)
 *     - Animates the saturation change by default
 *     - Can use any integer saturation percentage between 0 and 100 for finer adjustment or manual animation
 *     - Is not persistent across reboots
 *     - Enabled by running `cmd color_display set-saturation 100` (or 0)
 *
 * This is kind of an annoying limitation, because we want the functionality of method 2 but the permission persistence
 * of method 1 in case Shizuku isn't running. Therefore, we implement both and fall back to method 1 if method 2 fails.
 *
 * If you couldn't tell, this mess took me a while to figure out.
 *
 * This class also manages interfacing with Shizuku, which allows us to receive WRITE_SECURE_SETTINGS and directly
 * run the required shell commands for method 2. For more inforamtion on using Shizuku, see the [XDA article](https://www.xda-developers.com/implementing-shizuku/)
 * and [official docs](https://github.com/RikkaApps/Shizuku/blob/master/README.md).
 *
 * TODO: better way to inform the user that Shizuku isn't running if they reboot and greyscale partially breaks
 */
class GrayscaleController(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Whether we have permission to write secure settings, required to enable grayscale mode. */
    fun hasSecureSettingsAccess(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

    /** Whether the user has granted Shizuku permission. */
    fun shizukuPermissionGranted(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: IllegalStateException) {
        false
    }

    /** Whether the Shizuku app is installed, whether or not it's running. */
    fun shizukuAppInstalled(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.getVersion() > 0
    } catch (_: IllegalStateException) {
        false
    }

    /** Whether Shizuku is running and we can use it to grant permissions. */
    fun shizukuRunning(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.pingBinder()
    } catch (_: IllegalStateException) {
        false
    }

    /** Requests Shizuku permission from the user and waits for the result. */
    suspend fun requestShizukuPermission(requestCode: Int = REQUEST_CODE): Boolean {
        if (shizukuPermissionGranted()) return true
        if (!shizukuRunning()) return false

        // it's so cool that kotlin natively supports continuations omg
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(resultRequestCode: Int, grantResult: Int) {
                    if (resultRequestCode != requestCode || !continuation.isActive) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }

            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }

            try {
                Shizuku.requestPermission(requestCode)
            } catch (_: IllegalStateException) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resume(false)
            }
        }
    }

    /** Grants WRITE_SECURE_SETTINGS to this package through an authorized Shizuku process. */
    suspend fun grantThroughShizuku(): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuPermissionGranted()) return@withContext false
        try {
            val process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                arrayOf("sh", "-c", "pm grant ${appContext.packageName} android.permission.WRITE_SECURE_SETTINGS"),
                null, null
            )
            process.inputStream.close()
            process.errorStream.close()
            process.waitFor() == 0 && hasSecureSettingsAccess()
        } catch (_: Exception) {
            false
        }
    }

    enum class GrayscaleResult {
        FAILED_NO_PERMISSION,
        SUCCESS_ENABLED_CDS,
        SUCCESS_ENABLED_DALTONIZER,
        SUCCESS_DISABLED
    }

    /** Sets grayscale mode on or off. */
    suspend fun setGrayscale(enabled: Boolean): GrayscaleResult = withContext(Dispatchers.IO) {
        if (!hasSecureSettingsAccess()) return@withContext GrayscaleResult.FAILED_NO_PERMISSION
        try {
            if (enabled) {
                // attempt to enable with ColorDisplayService if we have Shizuku permission and it's running
                if (shizukuPermissionGranted() && shizukuRunning()) {
                    try {
                        val process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                            arrayOf("sh", "-c", "cmd color_display set-saturation 0"),
                            null, null
                        )
                        process.inputStream.close()
                        process.errorStream.close()
                        if (process.waitFor() == 0) return@withContext GrayscaleResult.SUCCESS_ENABLED_CDS
                    } catch (_: Exception) {
                        // Fall back to daltonizer if we can't use ColorDisplayService
                    }
                }

                setDaltonizerState(true)
                return@withContext GrayscaleResult.SUCCESS_ENABLED_DALTONIZER
            } else {
                // always disable with the daltonizer
                setDaltonizerState(false)

                // if we can with Shizuku, disable through it
                if (shizukuPermissionGranted() && shizukuRunning()) {
                    try {
                        val process = IShizukuService.Stub.asInterface(Shizuku.getBinder()).newProcess(
                            arrayOf("sh", "-c", "cmd color_display set-saturation 100"),
                            null, null
                        )
                        process.inputStream.close()
                        process.errorStream.close()
                        process.waitFor()
                    } catch (_: Exception) {}
                }

                return@withContext GrayscaleResult.SUCCESS_DISABLED
            }
        } catch (_: SecurityException) {
            GrayscaleResult.FAILED_NO_PERMISSION
        }
    }

    /** Update daltonizer settings in Settings.Secure */
    private fun setDaltonizerState(enabled: Boolean) {
        Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, if (enabled) 1 else 0)
        Settings.Secure.putInt(resolver, DALTONIZER_MODE, if (enabled) DALTONIZER_MODE_GRAYSCALE else DALTONIZER_MODE_DISABLED)
    }

    companion object {
        /** Request code for Shizuku permission requests */
        const val REQUEST_CODE = 1001

        private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER_MODE = "accessibility_display_daltonizer"

        private const val DALTONIZER_MODE_DISABLED = -1
        private const val DALTONIZER_MODE_GRAYSCALE = 0
    }
}