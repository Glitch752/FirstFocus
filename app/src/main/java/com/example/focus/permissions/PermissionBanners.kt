package com.example.focus.permissions

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun RequiredPermissionBanners(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Refresh permissions when the app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!state.isChecking) {
            state.missing.forEach { permission ->
                PermissionBanner(permission, context)
            }
        }
    }
}

@Composable
private fun PermissionBanner(permission: RequiredPermission, context: Context) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors().copy(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // weight prevents the button next to it from wrapping
            Column(Modifier.weight(1f)) {
                Text("${permission.title} required", style = MaterialTheme.typography.titleMedium)
                Text(permission.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { context.startActivity(permission.intent()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Grant")
            }
        }
    }
}