package com.gankdat.pomegranate.ui.connection

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.auth.AuthState
import com.gankdat.pomegranate.auth.CustomTabLauncher
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters

/** Google account connection status, sign-in/out, and Picker rounds. */
@Composable
internal fun ConnectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel = viewModel(factory = appViewModelFactory { ConnectionViewModel(it.auth, it.cloudScanner, it.reconciliation, it.origins) }),
) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val round by viewModel.roundState.collectAsStateWithLifecycle()
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    // Returning from the Picker tab: resume polling the open round, if any.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumeRoundIfPicking() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.connection_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        AuthSection(auth, viewModel, activity)

        if (auth == AuthState.SignedIn) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            PickerSection(round, coverage, viewModel, activity)
        }
    }
}

@Composable
private fun AuthSection(auth: AuthState, viewModel: ConnectionViewModel, activity: Activity?) {
    when (auth) {
        AuthState.NotConfigured -> Text(stringResource(R.string.connection_not_configured), color = MaterialTheme.colorScheme.error)
        AuthState.SignedOut -> Button(onClick = { activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.connection_sign_in))
        }
        AuthState.SigningIn -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.connection_signing_in))
            TextButton(onClick = viewModel::signOut) { Text(stringResource(android.R.string.cancel)) }
        }
        AuthState.SignedIn -> {
            Text(stringResource(R.string.connection_signed_in), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_sign_out))
            }
        }
        AuthState.NeedsReauth -> {
            Text(stringResource(R.string.connection_needs_reauth), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_sign_in_again))
            }
        }
        is AuthState.Error -> {
            Text(auth.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.dismissError(); activity?.let(viewModel::signIn) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.connection_try_again))
            }
            TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.cancel)) }
        }
    }
}

@Composable
private fun PickerSection(round: RoundState, coverage: CoverageUi, viewModel: ConnectionViewModel, activity: Activity?) {
    Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.picker_body), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pluralStringResource(R.plurals.picker_coverage, coverage.itemCount, coverage.itemCount, coverage.roundCount),
                style = MaterialTheme.typography.bodyLarge,
            )
            coverage.lastRoundAt?.let {
                Text(stringResource(R.string.picker_last_round, Formatters.dateTime(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    when (round) {
        RoundState.Idle -> Button(onClick = { activity?.let { a -> viewModel.startRound { url -> CustomTabLauncher.open(a, url) } } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.picker_start))
        }
        RoundState.Starting, RoundState.Indexing -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(if (round == RoundState.Starting) R.string.picker_starting else R.string.picker_indexing))
        }
        is RoundState.Picking -> {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.picker_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { activity?.let { a -> CustomTabLauncher.open(a, round.pickerUri) } }) {
                Text(stringResource(R.string.picker_reopen))
            }
            TextButton(onClick = viewModel::cancelRound) { Text(stringResource(android.R.string.cancel)) }
        }
        is RoundState.Done -> {
            Text(pluralStringResource(R.plurals.picker_round_done, round.itemCount, round.itemCount), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { activity?.let { a -> viewModel.startRound { url -> CustomTabLauncher.open(a, url) } } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.picker_another))
            }
        }
        is RoundState.Failed -> {
            Text(round.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::dismissRoundResult, modifier = Modifier.fillMaxWidth()) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
