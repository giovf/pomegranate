package com.gankdat.pomegranate.ui.pull

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.MascotPanel
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.components.SecondaryButton
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.manifest.fidelityLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.text.NumberFormat

/** Choosing what to copy, the copy in progress and its result, in one route. */
@Composable
internal fun PullScreen(
    onDone: () -> Unit,
    onViewProof: () -> Unit,
    onAutoDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PullViewModel = viewModel(
        factory = appViewModelFactory {
            PullViewModel(it.database.cloudItemDao(), it.database.transferJobDao(), it.reconciliation, it.transferQueue, it.transferEngine, it.transferScheduler, it.settings, it.pullSelection,
                it.cloudScanner.pickerRounds().map { rounds -> rounds.filter { r -> r.state == PickerRoundState.EXPIRED }.map { r -> r.sessionId }.toSet() },
            )
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

private fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ScopeContent(viewModel: PullViewModel, modifier: Modifier) {
    // The progress notification needs POST_NOTIFICATIONS; a refusal only hides it, the copy still runs.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.requestStart() }
    val context = LocalContext.current
    val startWithNotificationPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            viewModel.requestStart()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val options by viewModel.scopeOptions.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val closedCount by viewModel.closedCount.collectAsStateWithLifecycle()
    val selectedCount = options.firstOrNull { it.choice == selected }?.itemCount ?: 0
    val nothingToPull = options.isNotEmpty() && options.all { it.itemCount == 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(if (nothingToPull) R.string.pull_nothing else R.string.pull_scope_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (options.isNotEmpty()) {
            Panel {
                Column {
                    options.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = option.choice == selected, role = Role.RadioButton, onClick = { viewModel.select(option.choice) })
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            RadioButton(selected = option.choice == selected, onClick = null)
                            Column {
                                Text(scopeLabel(option.choice), style = MaterialTheme.typography.titleMedium)
                                Text(pluralStringResource(R.plurals.pull_scope_count, option.itemCount, option.itemCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (closedCount > 0) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Text(pluralStringResource(R.plurals.pull_closed_rounds, closedCount, closedCount), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(pluralStringResource(R.plurals.pull_start, selectedCount, selectedCount), onClick = startWithNotificationPermission, enabled = selectedCount > 0)
            Text(stringResource(R.string.pull_size_unknown), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.pull_history_title))
                history.forEach { batch -> PullBatchCard(batch, onOpen = { viewModel.openSummary(batch.batchId) }) }
            }
        }
    }
}

/** One finished copy: when, how much, and where it stands. Opens that copy's summary. */
@Composable
private fun PullBatchCard(batch: PullBatch, onOpen: () -> Unit) {
    val title = if (batch.batchId > 0) stringResource(R.string.pull_batch_title, Formatters.dateTime(batch.batchId)) else stringResource(R.string.pull_batch_earlier)
    val status = when {
        batch.failed > 0 -> pluralStringResource(R.plurals.pull_batch_failed, batch.failed, batch.failed)
        batch.cleanedUp -> stringResource(R.string.pull_batch_cleaned_up)
        else -> stringResource(R.string.pull_batch_verified)
    }
    Panel(modifier = Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.pull_done_summary, batch.done, batch.done, Formatters.fileSize(batch.bytesWritten)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.labelLarge, color = if (batch.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun scopeLabel(choice: ScopeChoice): String = when (choice) {
    ScopeChoice.Selected -> stringResource(R.string.pull_scope_selected)
    ScopeChoice.CloudOnly -> stringResource(R.string.pull_scope_cloud_only)
    ScopeChoice.Everything -> stringResource(R.string.pull_scope_everything)
    is ScopeChoice.Origin -> stringResource(R.string.pull_scope_origin, choice.label)
}

@Composable
private fun ProgressContent(viewModel: PullViewModel, modifier: Modifier) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val summary = progress.summary

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.pull_progress_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })

        val spoken = pluralStringResource(R.plurals.pull_progress_items, summary.total, summary.done, summary.total, Formatters.fileSize(summary.bytesWritten))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(220.dp)
                .clearAndSetSemantics { contentDescription = spoken },
            contentAlignment = Alignment.Center,
        ) {
            val ring = Modifier.fillMaxSize()
            // Google reports no sizes up front, so until the first item is done there is no honest fraction: show activity instead.
            if (summary.done == 0 && summary.isActive) {
                CircularProgressIndicator(modifier = ring, strokeWidth = 16.dp, trackColor = MaterialTheme.colorScheme.primaryContainer, strokeCap = StrokeCap.Round)
            } else {
                val fraction = if (summary.total == 0) 0f else summary.done.toFloat() / summary.total
                CircularProgressIndicator(progress = { fraction }, modifier = ring, strokeWidth = 16.dp, trackColor = MaterialTheme.colorScheme.primaryContainer, strokeCap = StrokeCap.Round, gapSize = 0.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count(summary.done), style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.pull_of_total, count(summary.total)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val eta = progress.etaSeconds
            Text(
                text = when {
                    progress.isPaused -> stringResource(R.string.pull_paused)
                    eta != null -> stringResource(R.string.pull_eta, Formatters.duration(eta * 1_000))
                    else -> stringResource(R.string.pull_speed_calculating)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            val speed = progress.bytesPerSecond
            if (!progress.isPaused && speed != null) {
                Text(stringResource(R.string.pull_speed, Formatters.fileSize(speed.toLong())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.pull_progress_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        val next = progress.pending.take(PENDING_SHOWN)
        if (next.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.pull_whats_left))
                Panel {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        next.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(modifier = Modifier.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(row.filename, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(jobStateLabel(row.job.state, row.liveBytes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (progress.isPaused) {
                PrimaryButton(stringResource(R.string.pull_resume), onClick = viewModel::resume)
            } else {
                SecondaryButton(stringResource(R.string.pull_pause), onClick = viewModel::pause)
            }
            TextButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.pull_stop)) }
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
    // Only a copy that has just run may start the disconnect countdown; reopening an old summary never does.
    val justFinished by viewModel.justFinished.collectAsStateWithLifecycle()
    BackHandler { viewModel.dismissCompletion() }
    var countdown by rememberSaveable { mutableStateOf(if (autoDisconnect && justFinished) AUTO_DISCONNECT_SECONDS else -1) }
    LaunchedEffect(autoDisconnect, justFinished, progress.failed.isEmpty()) {
        if (!autoDisconnect || !justFinished || progress.failed.isNotEmpty()) {
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (progress.failed.isEmpty() && summary.done > 0) MascotPanel(height = 190.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(R.plurals.pull_done_title, summary.done, count(summary.done)), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.pull_done_verified), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formatters.fileSize(summary.bytesWritten), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress.fidelityCounts.isNotEmpty()) {
            Panel {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.pull_done_fidelity), style = MaterialTheme.typography.titleMedium)
                    progress.fidelityCounts.forEach { (fidelity, howMany) ->
                        Text("${count(howMany)} × ${fidelityLabel(fidelity)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (progress.failed.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pluralStringResource(R.plurals.pull_failed_summary, progress.failed.size, progress.failed.size), style = MaterialTheme.typography.titleMedium)
                    progress.failed.take(MAX_FAILURES_SHOWN).forEach { row ->
                        Text("${row.filename}: ${row.job.lastError.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (progress.failed.size > MAX_FAILURES_SHOWN) {
                        Text(pluralStringResource(R.plurals.pull_failed_more, progress.failed.size - MAX_FAILURES_SHOWN, progress.failed.size - MAX_FAILURES_SHOWN), style = MaterialTheme.typography.bodyMedium)
                    }
                    SecondaryButton(stringResource(R.string.pull_retry_failed), onClick = viewModel::retryFailed)
                }
            }
        }
        if (countdown >= 0) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(pluralStringResource(R.plurals.auto_disconnect_countdown, countdown, countdown), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { countdown = -1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.auto_disconnect_cancel)) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(R.string.pull_view_proof), onClick = onViewProof)
            SecondaryButton(stringResource(R.string.pull_more), onClick = viewModel::dismissCompletion)
            TextButton(onClick = { viewModel.dismissCompletion(); onDone() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.pull_back_to_library))
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.fidelity_notice_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = dontShowAgain, role = Role.Checkbox, onClick = { dontShowAgain = !dontShowAgain })
                        .heightIn(min = 48.dp),
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = null)
                    Text(stringResource(R.string.fidelity_notice_dont_show), modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onContinue(dontShowAgain) }) { Text(stringResource(R.string.fidelity_notice_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private const val MAX_FAILURES_SHOWN = 20
private const val AUTO_DISCONNECT_SECONDS = 15
private const val PENDING_SHOWN = 4
