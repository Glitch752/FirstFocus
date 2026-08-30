package com.example.focus.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.focus.R
import com.example.focus.ui.modifiers.horizontalOverflow

/** For some reason, the default Material monospace font doesn't work for me, so we just ship one */
private val CodeFontFamily = FontFamily(Font(R.font.jetbrains_mono_regular))

private fun getApplicationName(context: Context): String {
    val stringId = context.applicationInfo.labelRes
    return if (stringId == 0) context.applicationInfo.nonLocalizedLabel?.toString() ?: context.packageName
        else context.getString(stringId)
}

private fun AnnotatedString.Builder.appendShizukuStatus(
    hasApp: Boolean,
    isRunning: Boolean,
    isAuthorized: Boolean
) {
    when {
        !hasApp -> {
            append("Shizuku is not installed. Install and start Shizuku to use this option.")
        }
        !isRunning -> {
            append("Shizuku is installed but not running")
            if (isAuthorized) append(" and is already authorized")
            append(". Start it to use this option.")
        }
        !isAuthorized -> append("Shizuku is installed and running, but it is not authorized. Authorize Shizuku to use this option.")
        else -> append("Shizuku is installed, running, and authorized.")
    }
}

private fun AnnotatedString.Builder.appendPermissionOverview(
    hasSystemPermission: Boolean,
    hasShizukuApp: Boolean,
    shizukuRunning: Boolean,
    hasShizukuPermission: Boolean,
    linkStyle: TextLinkStyles
) {
    append("Android requires elevated permissions for apps to change these system display settings. ")

    if (hasSystemPermission) {
        append("The system permission is already granted, so grayscale will function. ")
        if (hasShizukuApp && shizukuRunning && hasShizukuPermission) {
            append("Shizuku is also ready and will provide a smoother experience.")
        } else {
            append("Shizuku is optional, but it can provide smoother grayscale transitions and works better with Android's built-in grayscale modes.")
        }
    } else {
        append("The system permission is not granted yet. You can grant it in either of two ways: with ADB from a computer, or through Shizuku without connecting to a computer. ")
        if (hasShizukuApp && shizukuRunning && hasShizukuPermission) {
            append("Shizuku is installed, running, and authorized, so it is ready to grant the permission.")
        } else {
            append("Shizuku can also provide smoother grayscale transitions and works better with Android's built-in grayscale modes.")
        }
    }
}

/**
 * A card showing data about the current permission state related to grayscale mode and providing
 * buttons to grant or learn about the necessary permissions.
 */
@Composable
@Preview
private fun InfoAndPermissionCard(
    hasSystemPermission: Boolean = false,
    hasShizukuApp: Boolean = true,
    shizukuRunning: Boolean = true,
    hasShizukuPermission: Boolean = true,
    /** Called to tell the parent to update the permission state above */
    updatePermissionState: () -> Unit = {},
    viewModel: SettingsViewModel? = null
) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 0.9f
    )) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        )) {
            Column(Modifier.padding(vertical = 12.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // not sure why material doesn't handle this better by default
                val linkStyle = TextLinkStyles(
                    SpanStyle(color = MaterialTheme.colorScheme.primary),
                    hoveredStyle = SpanStyle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )

                Text(buildAnnotatedString {
                    appendLine("Grayscale mode allows you to stay more focused by reducing the visual appeal of your phone.")
                    appendLine("${getApplicationName(LocalContext.current)} can automatically enable grayscale mode during focus sessions.")
                    appendLine()
                    appendPermissionOverview(
                        hasSystemPermission = hasSystemPermission,
                        hasShizukuApp = hasShizukuApp,
                        shizukuRunning = shizukuRunning,
                        hasShizukuPermission = hasShizukuPermission,
                        linkStyle = linkStyle
                    )
                })

                @Composable
                fun subcard(title: String, success: Boolean, content: @Composable () -> Unit) {
                    Card(
                        Modifier.fillMaxWidth().horizontalOverflow(8.dp),
                        colors = if (success) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) else CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            content()
                        }
                    }
                }

                @Composable
                fun shizukuButton() {
                    val ctx = LocalContext.current
                    Button(
                        onClick = {
                            if (!hasShizukuApp) return@Button viewModel?.openShizukuInstructions() ?: Unit
                            if (!shizukuRunning) return@Button viewModel?.openShizukuStart() ?: Unit

                            if (hasShizukuPermission) {
                                viewModel?.grantThroughShizuku {
                                    updatePermissionState()
                                    Toast.makeText(ctx, if (it) "Permission granted through Shizuku" else "Failed to grant permission through Shizuku", Toast.LENGTH_SHORT).show()
                                }
                            } else viewModel?.requestShizukuPermission {
                                updatePermissionState()

                                // try to also grant the permission while we're at it
                                if (it) viewModel.grantThroughShizuku {
                                    updatePermissionState()
                                    Toast.makeText(ctx, if (hasSystemPermission) "Authorized Shizuku and granted permission" else "Authorized Shizuku but failed to grant permission", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(ctx, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        contentPadding = PaddingValues(start = 6.dp, end = 14.dp),
                    ) {
                        Image(painterResource(R.drawable.shizuku), "Shizuku logo", Modifier.size(32.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(when {
                            !hasShizukuApp -> "Install Shizuku"
                            !shizukuRunning -> "Start Shizuku"
                            !hasShizukuPermission -> "Authorize Shizuku"
                            else -> "Grant with Shizuku"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }

                val shizukuActive = hasShizukuApp && shizukuRunning && hasShizukuPermission

                if (!hasSystemPermission) {
                    subcard("ADB (system permission) not granted", false) {
                        Text(buildAnnotatedString {
                            withLink(LinkAnnotation.Url("https://developer.android.com/tools/adb", linkStyle)) { append("ADB") }
                            append(" can grant us permission by connecting your phone to a computer. ")
                            appendLine("Connect your phone to a computer with ADB installed and run this command:")
                            val packageName = LocalContext.current.packageName
                            withStyle(SpanStyle(
                                fontFamily = CodeFontFamily,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize
                            )) {
                                append("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
                            }
                            if (shizukuActive) {
                                append("\n\nYou can also grant the permission through Shizuku, which is already active.")
                            }
                        })

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(top = 4.dp).horizontalOverflow(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = { viewModel?.openAdbInstructions() },
                                contentPadding = PaddingValues(start = 10.dp, end = 14.dp)
                            ) {
                                Icon(Icons.Default.Adb, "ADB logo", Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Learn about ADB", style = MaterialTheme.typography.bodySmall)
                            }
                            if (shizukuActive) shizukuButton()
                        }
                    }
                } else {
                    subcard("System permission (ADB/Shizuku) granted", true) {
                        Text("Grayscale mode will work!")
                    }
                }

                subcard(if (shizukuActive) "Shizuku active" else "Shizuku not active", shizukuActive) {
                    Text(buildAnnotatedString {
                        withLink(LinkAnnotation.Url("https://shizuku.rikka.app/", linkStyle)) { append("Shizuku") }
                        appendLine(" can grant the system permission without connecting to a computer. It also enables smoother grayscale transitions and works better with Android's built-in grayscale modes.")
                        appendShizukuStatus(hasApp = hasShizukuApp, isRunning = shizukuRunning, isAuthorized = hasShizukuPermission)
                    })

                    if (!shizukuActive) shizukuButton()
                }

                val permissionStatus = when {
                    // shouldn't really happen
                    !hasSystemPermission && hasShizukuPermission && shizukuRunning -> "Shizuku authorized, but the ADB permission is not set."
                    !hasSystemPermission -> "No ADB permission or Shizuku permission granted."
                    hasShizukuPermission && shizukuRunning -> "Using Shizuku!"
                    !shizukuRunning -> "ADB permission set, but no Shizuku found!"
                    else -> "ADB permission set, but not authorized for Shizuku."
                }
                Text(
                    permissionStatus,
                    color = if (hasSystemPermission && hasShizukuPermission && shizukuRunning) {
                        MaterialTheme.colorScheme.primary
                    } else if (!hasSystemPermission || !shizukuRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun GrayscaleSettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = viewModel()
) {
    val enabled by viewModel.grayscaleDuringFocus.collectAsStateWithLifecycle()
    var hasSystemPermission by remember { mutableStateOf(viewModel.hasSecureSettingsAccess()) }
    var hasShizukuPermission by remember { mutableStateOf(viewModel.hasShizukuPermission()) }
    var hasShizukuApp by remember { mutableStateOf(viewModel.hasShizukuAppInstalled()) }
    var shizukuRunning by remember { mutableStateOf(viewModel.isShizukuRunning()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Grayscale mode") },
            windowInsets = WindowInsets(0.dp),
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        Box(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                //if (BuildConfig.DEBUG) {
                //    // for debugging the permission dialogs
                //    Card(Modifier.fillMaxWidth()) {
                //        @Composable
                //        fun DebugToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                //            Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                //                Text(label, style = MaterialTheme.typography.bodySmall)
                //                Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.6f))
                //            }
                //        }
                //        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                //            Text("Debug state", style = MaterialTheme.typography.titleSmall)
                //            DebugToggle("System permission granted", hasSystemPermission) { hasSystemPermission = it }
                //            DebugToggle("Shizuku installed", hasShizukuApp) { hasShizukuApp = it }
                //            DebugToggle("Shizuku running", shizukuRunning) { shizukuRunning = it }
                //            DebugToggle("Shizuku authorized", hasShizukuPermission) { hasShizukuPermission = it }
                //        }
                //    }
                //}
                InfoAndPermissionCard(
                    hasSystemPermission = hasSystemPermission,
                    hasShizukuApp = hasShizukuApp,
                    shizukuRunning = shizukuRunning,
                    hasShizukuPermission = hasShizukuPermission,
                    updatePermissionState = {
                        hasSystemPermission = viewModel.hasSecureSettingsAccess()
                        hasShizukuPermission = viewModel.hasShizukuPermission()
                        hasShizukuApp = viewModel.hasShizukuAppInstalled()
                        shizukuRunning = viewModel.isShizukuRunning()
                    },
                    viewModel = viewModel
                )

                // settings
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = hasSystemPermission) {
                        if (hasSystemPermission) viewModel.setGrayscaleDuringFocus(!enabled)
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Grayscale during focus sessions",
                        color = if (hasSystemPermission) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onBackground
                    )
                    Switch(
                        checked = enabled && hasSystemPermission,
                        enabled = hasSystemPermission,
                        onCheckedChange = viewModel::setGrayscaleDuringFocus,
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }
    }
}