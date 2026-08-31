package app.enlily.focus.permissions

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RequiredPermissionBanners(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    /** If there's a permission request that requires a confirmation modal, this will be set to it */
    var pendingRequest by remember { mutableStateOf<PendingPermissionRequest?>(null) }
    /** Request the given permission by starting its intent */
    fun requestPermission(permission: RequiredPermission) = context.startActivity(permission.intent())

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
                PermissionBanner(
                    permission = permission,
                    onGrant = {
                        val modal = permission.confirmModal()
                        if (modal == null) requestPermission(permission)
                        else pendingRequest = PendingPermissionRequest(permission, modal)
                    }
                )
            }
        }
    }

    pendingRequest?.let { request ->
        request.modal.content(
            {
                pendingRequest = null
                requestPermission(request.permission)
            },
            { pendingRequest = null }
        )
    }
}

private data class PendingPermissionRequest(
    val permission: RequiredPermission,
    val modal: ConfirmPermissionModal
)

@Composable
private fun PermissionBanner(
    permission: RequiredPermission,
    onGrant: () -> Unit
) {
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
                onClick = onGrant,
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