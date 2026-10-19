package com.gankdat.pomegranate.ui.pull

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import kotlinx.coroutines.delay

/** Scope choice, live progress and completion summary for a pull, in one route. */
@Composable
internal fun PullScreen(
    onDone: () -> Unit,
    onViewProof: () -> Unit,
    onAutoDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PullViewModel = viewModel(
        factory = appViewModelFactory {
            PullViewModel(it.database.cloudItemDao(), it.database.transferJobDao(), it.reconciliation, it.transferQueue, it.transferEngine, it.transferScheduler, it.settings)
        },
    ),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val showNotice by viewModel.showFidelityNotice.collectAsStateWithLifecycle()

    when (phase) {
        PullPhase.ChooseScope -> ScopeContent(viewModel, modifier)
        PullPhase.InProgress -> ProgressContent(viewModel, modifier)
        PullPhase.Completed -> CompletionContent(viewModel, onDone, onViewProof, onAutoDisconnect, modifier)
    }
    if (showNotice) {
        FidelityNoticeDialog(onContinue = viewModel::acknowledgeFidelityNotice, onDismiss = viewModel::dismissFidelityNotice)
    }
}

@Composable
private fun ScopeContent(viewModel: PullViewModel, modifier: Modifier) {
    val options by viewModel.scopeOptions.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val selectedCount = options.firstOrNull { it.choice == selected }?.itemCount ?: 0

    val nothingToPull = options.isNotEmpty() && options.all { it.itemCount == 0 }
    Column(modifier = modifier.fillMaxSize()) {
        Text(stringResource(R.string.pull_scope_intro), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        if (nothingToPull) {
            Text(
                stringResource(R.string.pull_nothing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(options, key = { it.choice.toString() }) { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option.choice == selected, role = Role.RadioButton, onClick = { viewModel.select(option.choice) })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    RadioButton(selected = option.choice == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(scopeLabel(option.choice), style = MaterialTheme.typography.bodyLarge)
                        Text(pluralStringResource(R.plurals.pull_scope_count, option.itemCount, option.itemCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(stringResource(R.string.pull_size_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        Button(
            onClick = viewModel::requestStart,
            enabled = selectedCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(pluralStringResource(R.plurals.pull_start, selectedCount, selectedCount))
        }
    }
}

@Composable
private fun scopeLabel(choice: ScopeChoice): String = when (choice) {
    ScopeChoice.CloudOnly -> stringResource(R.string.pull_scope_cloud_only)
    ScopeChoice.Everything -> stringResource(R.string.pull_scope_everything)
    is ScopeChoice.Origin -> stringResource(R.string.pull_scope_origin, choice.label)
}

@Composable
private fun ProgressContent(viewModel: PullViewModel, modifier: Modifier) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val summary = progress.summary

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val fraction = if (summary.total == 0) 0f else summary.done.toFloat() / summary.total
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(pluralStringResource(R.plurals.pull_progress_items, summary.total, summary.done, summary.total, Formatters.fileSize(summary.bytesWritten)), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when {
                    progress.isPaused -> stringResource(R.string.pull_paused)
                    progress.bytesPerSecond != null -> stringResource(R.string.pull_speed, Formatters.fileSize(progress.bytesPerSecond!!.toLong()))
                    else -> stringResource(R.string.pull_speed_calculating)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            progress.etaSeconds?.let {
                Text(stringResource(R.string.pull_eta, Formatters.duration(it * 1_000)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress.isPaused) {
                    Button(onClick = viewModel::resume) { Text(stringResource(R.string.pull_resume)) }
                } else {
                    OutlinedButton(onClick = viewModel::pause) { Text(stringResource(R.string.pull_pause)) }
                }
                TextButton(onClick = { confirmCancel = true }) { Text(stringResource(android.R.string.cancel)) }
            }
        }
        Text(stringResource(R.string.pull_whats_left), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(progress.pending, key = { it.job.id }) { row ->
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.filename, style = MaterialTheme.typography.bodyMedium)
                        Text(jobStateLabel(row.job.state, row.liveBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.pull_cancel_title)) },
            text = { Text(stringResource(R.string.pull_cancel_body)) },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text(stringResource(R.string.pull_cancel_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.pull_cancel_keep)) } },
        )
    }
}

@Composable
private fun jobStateLabel(state: TransferState, liveBytes: Long): String = when (state) {
    TransferState.QUEUED -> stringResource(R.string.job_queued)
    TransferState.RUNNING -> stringResource(R.string.job_running, Formatters.fileSize(liveBytes))
    TransferState.VERIFYING -> stringResource(R.string.job_verifying)
    TransferState.PAUSED -> stringResource(R.string.job_paused)
    TransferState.DONE -> stringResource(R.string.job_done)
    TransferState.FAILED -> stringResource(R.string.job_failed)
}

@Composable
private fun CompletionContent(viewModel: PullViewModel, onDone: () -> Unit, onViewProof: () -> Unit, onAutoDisconnect: () -> Unit, modifier: Modifier) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val autoDisconnect by viewModel.autoDisconnect.collectAsStateWithLifecycle()
    val summary = progress.summary
    var countdown by rememberSaveable { mutableStateOf(if (autoDisconnect) AUTO_DISCONNECT_SECONDS else -1) }
    LaunchedEffect(autoDisconnect, progress.failed.isEmpty()) {
        if (!autoDisconnect || progress.failed.isNotEmpty()) {
            countdown = -1
            return@LaunchedEffect
        }
        countdown = AUTO_DISCONNECT_SECONDS
        while (countdown > 0) {
            delay(1_000)
            if (countdown > 0) countdown--
        }
        if (countdown == 0) {
            viewModel.dismissCompletion()
            onAutoDisconnect()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(pluralStringResource(R.plurals.pull_done_summary, summary.done, summary.done, Formatters.fileSize(summary.bytesWritten)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pull_done_verified), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (progress.failed.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(pluralStringResource(R.plurals.pull_failed_summary, progress.failed.size, progress.failed.size), style = MaterialTheme.typography.titleMedium)
                    progress.failed.take(MAX_FAILURES_SHOWN).forEach { row ->
                        Text("${row.filename}: ${row.job.lastError.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (progress.failed.size > MAX_FAILURES_SHOWN) Text(pluralStringResource(R.plurals.pull_failed_more, progress.failed.size - MAX_FAILURES_SHOWN, progress.failed.size - MAX_FAILURES_SHOWN), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::retryFailed, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.pull_retry_failed)) }
                }
            }
        }
        if (countdown >= 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(pluralStringResource(R.plurals.auto_disconnect_countdown, countdown, countdown), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { countdown = -1 }) { Text(stringResource(R.string.auto_disconnect_cancel)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onViewProof,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.pull_view_proof))
        }
        Button(
            onClick = { viewModel.dismissCompletion(); onDone() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.pull_back_to_library))
        }
    }
}

@Composable
private fun FidelityNoticeDialog(onContinue: (dontShowAgain: Boolean) -> Unit, onDismiss: () -> Unit) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fidelity_notice_title)) },
        text = {
            Column {
                Text(stringResource(R.string.fidelity_notice_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Text(stringResource(R.string.fidelity_notice_dont_show))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onContinue(dontShowAgain) }) { Text(stringResource(R.string.fidelity_notice_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private const val MAX_FAILURES_SHOWN = 20
private const val AUTO_DISCONNECT_SECONDS = 15
