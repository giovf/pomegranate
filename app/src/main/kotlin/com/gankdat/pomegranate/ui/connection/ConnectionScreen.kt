package com.gankdat.pomegranate.ui.connection

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.auth.AuthState
import com.gankdat.pomegranate.auth.CustomTabLauncher
import com.gankdat.pomegranate.auth.TokenStatus
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.components.SecondaryButton
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.theme.MediaStateColors

/**
 * Google Photos: connecting, and then choosing photos batch by batch. Before
 * sign-in it only explains and offers the one button; afterwards it leads with
 * the next batch and keeps the connection details folded away.
 */
@Composable
internal fun ConnectionScreen(
    onDisconnect: () -> Unit,
    onOpenCoverage: () -> Unit,
    /** Opens the copy screen; the batch to copy has already been made the selection. */
    onPull: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = viewModel(
        factory = appViewModelFactory {
            ConnectionViewModel(it.auth, it.cloudScanner, it.reconciliation, it.origins, it.settings, it.database.cloudItemDao(), it.database.transferJobDao(), it.pullSelection)
        },
    ),
) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val round by viewModel.roundState.collectAsStateWithLifecycle()
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()
    val tokenStatus by viewModel.tokenStatus.collectAsStateWithLifecycle()
    val rounds by viewModel.rounds.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // Returning from the Picker tab: resume polling the open batch, if any.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.resumeRoundIfPicking()
        viewModel.checkRounds()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (auth == AuthState.SignedIn) {
            val pullRound = { sessionId: String ->
                viewModel.selectRoundForPull(sessionId)
                onPull()
            }
            Intro(stringResource(R.string.picker_title), stringResource(R.string.home_choose_body))
            NextBatch(round, coverage, viewModel, activity, pullRound)
            if (rounds.isNotEmpty()) ChosenSoFar(rounds, coverage, pullRound)
            HowItWorks()
            ConnectionDetails(tokenStatus, refreshing, viewModel::refreshAccess, onOpenCoverage, onDisconnect)
        } else {
            Intro(stringResource(R.string.connection_sign_in), stringResource(R.string.connect_intro))
            Reassurances()
            SignIn(auth, viewModel, activity)
        }
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
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

/** What connecting does and does not allow, before the user is asked to do it. */
@Composable
private fun Reassurances() {
    Panel {
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            listOf(R.string.connect_point_see, R.string.connect_point_change, R.string.connect_point_leave).forEachIndexed { index, res ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.heightIn(min = 68.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SignIn(auth: AuthState, viewModel: ConnectionViewModel, activity: Activity?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (auth) {
            AuthState.NotConfigured -> Text(stringResource(R.string.connection_not_configured), color = MaterialTheme.colorScheme.error)
            AuthState.SigningIn -> {
                Busy(stringResource(R.string.connection_signing_in))
                TextButton(onClick = viewModel::abandonSignIn, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(android.R.string.cancel)) }
            }
            AuthState.NeedsReauth -> {
                Text(stringResource(R.string.connection_needs_reauth), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(stringResource(R.string.connection_sign_in_again), onClick = { activity?.let(viewModel::signIn) })
            }
            is AuthState.Error -> {
                Text(auth.message, color = MaterialTheme.colorScheme.error)
                PrimaryButton(stringResource(R.string.connection_try_again), onClick = { viewModel.dismissError(); activity?.let(viewModel::signIn) })
                TextButton(onClick = viewModel::dismissError, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(android.R.string.cancel)) }
            }
            else -> {
                PrimaryButton(stringResource(R.string.connection_sign_in), onClick = { activity?.let(viewModel::signIn) })
                Text(stringResource(R.string.connect_footnote), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The next batch: start it, follow it while Google's picker is open, and copy it when it is done. */
@Composable
private fun NextBatch(round: RoundState, coverage: CoverageUi, viewModel: ConnectionViewModel, activity: Activity?, onPullRound: (String) -> Unit) {
    var showQr by rememberSaveable { mutableStateOf(false) }
    val startOnPhone = { activity?.let { a -> viewModel.startRound { url -> CustomTabLauncher.open(a, url) } } }
    val startElsewhere = { viewModel.startRound { showQr = true } }
    val nextRound = coverage.roundCount + 1

    Panel {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (round) {
                RoundState.Idle -> StartBatch(nextRound, coverage, onPhone = { startOnPhone() }, onComputer = startElsewhere)
                RoundState.Starting -> Busy(stringResource(R.string.picker_starting))
                RoundState.Indexing -> Busy(stringResource(R.string.picker_indexing))
                is RoundState.Picking -> {
                    Busy(stringResource(R.string.picker_round_open, nextRound), emphasised = true)
                    Text(stringResource(R.string.picker_waiting), style = MaterialTheme.typography.bodyLarge)
                    round.expiresAt?.let {
                        Text(stringResource(R.string.picker_expires, Formatters.time(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryButton(stringResource(R.string.picker_continue_phone), onClick = { activity?.let { a -> CustomTabLauncher.open(a, round.pickerUri) } })
                    SecondaryButton(stringResource(R.string.picker_continue_computer), onClick = { showQr = true })
                    TextButton(onClick = viewModel::cancelRound, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.picker_cancel_round)) }
                }
                is RoundState.Done -> {
                    val text = if (round.oldest != null && round.newest != null) {
                        pluralStringResource(R.plurals.picker_round_done_range, round.itemCount, nextRound - 1, round.itemCount, Formatters.date(round.oldest), Formatters.date(round.newest))
                    } else {
                        pluralStringResource(R.plurals.picker_round_done, round.itemCount, nextRound - 1, round.itemCount)
                    }
                    Text(text, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.picker_round_done_next), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryButton(stringResource(R.string.round_pull_now), onClick = { onPullRound(round.sessionId) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StartBatch(nextRound, coverage, onPhone = { startOnPhone() }, onComputer = startElsewhere, quiet = true)
                }
                is RoundState.Failed -> {
                    Text(round.message, color = MaterialTheme.colorScheme.error)
                    PrimaryButton(stringResource(android.R.string.ok), onClick = viewModel::dismissRoundResult)
                }
            }
        }
    }
    if (showQr && round is RoundState.Picking) {
        PickOnAnotherDeviceDialog(
            pickerUri = round.pickerUri,
            continueBefore = coverage.lastRound?.range?.oldest,
            onDismiss = { showQr = false },
        )
    }
}

/** [quiet] drops the filled button, for when copying the batch just finished is the main action. */
@Composable
private fun StartBatch(number: Int, coverage: CoverageUi, onPhone: () -> Unit, onComputer: () -> Unit, quiet: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.picker_start_heading, number), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        val lastOldest = coverage.lastRound?.range?.oldest
        Text(
            text = if (lastOldest != null) stringResource(R.string.picker_continue_from, Formatters.date(lastOldest)) else stringResource(R.string.picker_continue_from_start),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (quiet) {
        SecondaryButton(stringResource(R.string.picker_start), onClick = onPhone)
    } else {
        PrimaryButton(stringResource(R.string.picker_start), onClick = onPhone)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.picker_start_other_device), onClick = onComputer)
        Text(stringResource(R.string.picker_start_other_device_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChosenSoFar(rounds: List<RoundSummary>, coverage: CoverageUi, onPullRound: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rounds_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        if (coverage.roundCount > 0) {
            Text(pluralStringResource(R.plurals.picker_coverage, coverage.itemCount, coverage.itemCount, coverage.roundCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rounds.forEachIndexed { index, summary ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BatchRow(summary, onPullRound)
        }
    }
}

@Composable
private fun BatchRow(summary: RoundSummary, onPullRound: (String) -> Unit) {
    val round = summary.round
    val pending = summary.pending
    val hasItems = (summary.range?.itemCount ?: 0) > 0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when (round.state) {
                        PickerRoundState.INDEXED -> {
                            val selected = pluralStringResource(R.plurals.round_selected, summary.selected, summary.selected)
                            val overlap = summary.alreadyIndexed?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.round_already_indexed, it, it) }
                            listOfNotNull(selected, overlap).joinToString(", ")
                        }
                        PickerRoundState.ACTIVE -> stringResource(R.string.round_active)
                        // A batch that was listed and later closed by Google still has items pointing at it.
                        PickerRoundState.EXPIRED -> stringResource(if (hasItems) R.string.round_closed else R.string.round_expired)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val range = summary.range
                val span = if (range?.oldest != null && range.newest != null) stringResource(R.string.round_span, Formatters.date(range.oldest), Formatters.date(range.newest)) else null
                Text(
                    text = listOfNotNull(stringResource(R.string.round_started, Formatters.dateTime(round.createdAt)), span).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (round.state == PickerRoundState.INDEXED && pending == null && hasItems) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val green = MediaStateColors.of(MediaState.DEVICE_ONLY)
                    Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = green, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.batch_on_phone), style = MaterialTheme.typography.labelLarge, color = green)
                }
            }
        }
        when {
            round.state == PickerRoundState.INDEXED && pending != null -> {
                Text(pluralStringResource(R.plurals.round_pending, pending.count, pending.count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                SecondaryButton(stringResource(R.string.round_pull_now), onClick = { onPullRound(round.sessionId) })
            }
            round.state == PickerRoundState.EXPIRED && pending != null -> ChooseAgain(pending)
            round.state == PickerRoundState.EXPIRED && hasItems ->
                Text(stringResource(R.string.round_closed_nothing_lost), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** For a batch Google closed: how many still need copying and which dates to choose again. */
@Composable
private fun ChooseAgain(pending: RoundPending) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pluralStringResource(R.plurals.repick_title, pending.count, pending.count), style = MaterialTheme.typography.titleMedium)
            if (pending.oldest != null && pending.newest != null) {
                Text(stringResource(R.string.repick_range, Formatters.date(pending.oldest), Formatters.date(pending.newest)), style = MaterialTheme.typography.bodyLarge)
                val shown = pending.months.take(REPICK_MONTHS_SHOWN)
                val months = shown.joinToString { (month, count) ->
                    "${Formatters.monthYear(month.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())} ($count)"
                }
                val more = pending.months.size - shown.size
                Text(
                    if (more > 0) pluralStringResource(R.plurals.repick_months_more, more, months, more) else stringResource(R.string.repick_months, months),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(stringResource(R.string.repick_no_dates), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.repick_how), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private const val REPICK_MONTHS_SHOWN = 8

/** A row that opens and closes the content under it; closed to begin with so the screen stays short. */
@Composable
private fun Fold(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.group_collapse else R.string.group_expand)) { open = !open }
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Column(modifier = Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun HowItWorks() {
    Fold(stringResource(R.string.picker_how_title)) {
        Text(stringResource(R.string.picker_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Sign-in status, the month-by-month view and disconnecting: there when wanted, out of the way otherwise. */
@Composable
private fun ConnectionDetails(status: TokenStatus?, refreshing: Boolean, onRefresh: () -> Unit, onOpenCoverage: () -> Unit, onDisconnect: () -> Unit) {
    Fold(stringResource(R.string.connection_details_title)) {
        Text(stringResource(R.string.connection_scope), style = MaterialTheme.typography.bodyLarge)
        if (status != null) {
            val now = System.currentTimeMillis()
            val daysSinceSignIn = if (status.signedInAtMillis > 0) ((now - status.signedInAtMillis) / 86_400_000L).toInt() else null
            Text(
                if (status.accessValidUntilMillis > now) stringResource(R.string.token_valid_until, Formatters.dateTime(status.accessValidUntilMillis)) else stringResource(R.string.token_needs_refresh),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (daysSinceSignIn != null) {
                Text(
                    pluralStringResource(R.plurals.token_signed_in_days, daysSinceSignIn, daysSinceSignIn),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (daysSinceSignIn >= TESTING_MODE_WARN_DAYS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.token_testing_mode_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.token_refresh)) }
        }
        TextButton(onClick = onOpenCoverage, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.coverage_open)) }
        SecondaryButton(stringResource(R.string.connection_sign_out), onClick = onDisconnect)
    }
}

private const val TESTING_MODE_WARN_DAYS = 6

@Composable
private fun Busy(text: String, emphasised: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, trackColor = Color.Transparent)
        Text(text, style = if (emphasised) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
    }
}
