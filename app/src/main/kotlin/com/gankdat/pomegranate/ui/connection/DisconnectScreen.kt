package com.gankdat.pomegranate.ui.connection

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.auth.CustomTabLauncher
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.components.SecondaryButton
import com.gankdat.pomegranate.ui.library.ThumbnailLoader
import com.gankdat.pomegranate.ui.theme.MediaStateColors

private const val GOOGLE_PERMISSIONS_URL = "https://myaccount.google.com/permissions"

/** Says what stays and what goes, then removes access, wipes, and asks the user to check at Google; the stack is replaced on completion. */
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (val p = phase) {
            is DisconnectPhase.PreCheck -> {
                Intro(stringResource(R.string.disconnect_title), stringResource(R.string.disconnect_body))
                Panel {
                    Text(stringResource(R.string.disconnect_keep_title), style = MaterialTheme.typography.labelLarge, color = MediaStateColors.of(MediaState.DEVICE_ONLY))
                    listOf(R.string.disconnect_keep_files, R.string.disconnect_keep_proof, R.string.disconnect_keep_google).forEach { MarkedRow(stringResource(it), kept = true) }
                }
                Panel {
                    Text(stringResource(R.string.disconnect_remove_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    DisconnectStep.entries.forEach { MarkedRow(stringResource(it.labelRes()), kept = false) }
                }
                val pending = p.pending.queued + p.pending.running + p.pending.verifying + p.pending.paused
                if (pending > 0) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(pluralStringResource(R.plurals.disconnect_pending, pending, pending), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = viewModel::cancelPendingTransfers, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.disconnect_cancel_transfers)) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrimaryButton(stringResource(R.string.disconnect_now), onClick = { confirm = true })
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.disconnect_not_now)) }
                }
            }
            is DisconnectPhase.Running -> {
                Text(stringResource(R.string.disconnect_running), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Panel {
                    DisconnectStep.entries.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                when {
                                    step in p.completed -> Icon(painter = painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.step_done), tint = MediaStateColors.of(MediaState.DEVICE_ONLY))
                                    step == p.current -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, trackColor = Color.Transparent)
                                }
                            }
                            Text(stringResource(step.labelRes()), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            is DisconnectPhase.Complete -> {
                Intro(stringResource(R.string.disconnect_done_title), stringResource(R.string.disconnect_done_body, p.counts.cloudItems, p.counts.rounds))
                Panel {
                    Text(stringResource(R.string.disconnect_verify_body), style = MaterialTheme.typography.bodyLarge)
                    SecondaryButton(stringResource(R.string.disconnect_verify_button), onClick = { activity?.let { CustomTabLauncher.open(it, GOOGLE_PERMISSIONS_URL) } })
                    Text(stringResource(R.string.disconnect_verify_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = p.confirmedAtGoogle, role = Role.Checkbox, onValueChange = viewModel::setConfirmedAtGoogle)
                            .heightIn(min = 48.dp),
                    ) {
                        Checkbox(checked = p.confirmedAtGoogle, onCheckedChange = null)
                        Text(stringResource(R.string.disconnect_confirm_checkbox), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                PrimaryButton(stringResource(R.string.disconnect_continue), onClick = onFinished)
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

@Composable
private fun Intro(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** One thing that stays ([kept], a green tick) or goes (a ruby cross). The mark is decoration: the group heading says which it is. */
@Composable
private fun MarkedRow(text: String, kept: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            painter = painterResource(if (kept) R.drawable.ic_check else R.drawable.ic_close),
            contentDescription = null,
            tint = if (kept) MediaStateColors.of(MediaState.DEVICE_ONLY) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun DisconnectStep.labelRes(): Int = when (this) {
    DisconnectStep.REVOKE -> R.string.disconnect_step_revoke
    DisconnectStep.WIPE_SECRETS -> R.string.disconnect_step_secrets
    DisconnectStep.WIPE_CLOUD -> R.string.disconnect_step_cloud
    DisconnectStep.CLEAR_CACHE -> R.string.disconnect_step_cache
}
