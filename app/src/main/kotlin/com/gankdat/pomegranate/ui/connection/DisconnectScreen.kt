package com.gankdat.pomegranate.ui.connection

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.auth.CustomTabLauncher
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.library.ThumbnailLoader

private const val GOOGLE_PERMISSIONS_URL = "https://myaccount.google.com/permissions"

/** Revoke, wipe, then verify at Google; the stack is replaced on completion. */
@Composable
internal fun DisconnectScreen(
    onFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DisconnectViewModel = viewModel(
        factory = appViewModelFactory { app ->
            DisconnectViewModel(app.auth, app.transferQueue, app.transferScheduler, app.cloudDataWiper, clearThumbnails = { ThumbnailLoader.clearShared() })
        },
    ),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        when (val p = phase) {
            is DisconnectPhase.PreCheck -> {
                Text(stringResource(R.string.disconnect_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.disconnect_body), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                listOf(R.string.disconnect_step_revoke, R.string.disconnect_step_secrets, R.string.disconnect_step_cloud, R.string.disconnect_step_cache, R.string.disconnect_keeps)
                    .forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                val pending = p.pending.queued + p.pending.running + p.pending.verifying + p.pending.paused
                if (pending > 0) {
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(pluralStringResource(R.plurals.disconnect_pending, pending, pending), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = viewModel::cancelPendingTransfers) { Text(stringResource(R.string.disconnect_cancel_transfers)) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect_now)) }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect_not_now)) }
            }
            is DisconnectPhase.Running -> {
                Text(stringResource(R.string.disconnect_running), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                DisconnectStep.entries.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        when {
                            step in p.completed -> Icon(painter = painterResource(R.drawable.ic_state_both), contentDescription = stringResource(R.string.step_done), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            step == p.current -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            else -> Spacer(Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(step.labelRes()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            is DisconnectPhase.Complete -> {
                Text(stringResource(R.string.disconnect_done_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.disconnect_done_body, p.counts.cloudItems, p.counts.rounds), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.disconnect_verify_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { activity?.let { CustomTabLauncher.open(it, GOOGLE_PERMISSIONS_URL) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.disconnect_verify_button))
                }
                Text(stringResource(R.string.disconnect_verify_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = p.confirmedAtGoogle, onCheckedChange = viewModel::setConfirmedAtGoogle)
                    Text(stringResource(R.string.disconnect_confirm_checkbox))
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect_continue)) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.disconnect_confirm_title)) },
            text = { Text(stringResource(if (viewModel.hasPendingTransfers) R.string.disconnect_confirm_body_pending else R.string.disconnect_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.disconnect() }) { Text(stringResource(R.string.disconnect_now)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

private fun DisconnectStep.labelRes(): Int = when (this) {
    DisconnectStep.REVOKE -> R.string.disconnect_step_revoke
    DisconnectStep.WIPE_SECRETS -> R.string.disconnect_step_secrets
    DisconnectStep.WIPE_CLOUD -> R.string.disconnect_step_cloud
    DisconnectStep.CLEAR_CACHE -> R.string.disconnect_step_cache
}
