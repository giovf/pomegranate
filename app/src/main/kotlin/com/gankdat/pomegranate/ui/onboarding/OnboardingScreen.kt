package com.gankdat.pomegranate.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.MascotPanel
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.components.PromiseRow
import com.gankdat.pomegranate.ui.components.SecondaryButton

/**
 * First run, in two steps: a welcome that says what the app does and what it
 * never does, then the one decision that cannot wait, where copies are saved.
 */
@Composable
internal fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(
        factory = appViewModelFactory { OnboardingViewModel(it.settings, ResolverDestinationAccess(it.contentResolver)) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var choosingFolder by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onDestinationPicked(uri)
    }
    BackHandler(enabled = choosingFolder) { choosingFolder = false }

    if (choosingFolder) {
        DestinationStep(
            state = state,
            onChooseFolder = { picker.launch(null) },
            onBack = { choosingFolder = false },
            onDone = { viewModel.completeOnboarding(onFinished) },
            modifier = modifier,
        )
    } else {
        WelcomeStep(onContinue = { choosingFolder = true }, modifier = modifier)
    }

    if (state.showBackupWarning) {
        AlertDialog(
            onDismissRequest = viewModel::acknowledgeBackupWarning,
            title = { Text(stringResource(R.string.backup_warning_title)) },
            text = { Text(stringResource(R.string.backup_warning_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeBackupWarning(); picker.launch(null) }) { Text(stringResource(R.string.backup_warning_change)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::acknowledgeBackupWarning) { Text(stringResource(R.string.backup_warning_keep)) }
            },
        )
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(stringResource(R.string.destination_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (error) {
                            OnboardingError.PERMISSION_NOT_PERSISTABLE -> R.string.destination_error_not_persistable
                            OnboardingError.DESTINATION_REVOKED -> R.string.destination_error_revoked
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        MascotPanel(height = 260.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.onboarding_purpose_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.onboarding_purpose_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PromiseRow(stringResource(R.string.onboarding_promise_on_device))
            PromiseRow(stringResource(R.string.onboarding_promise_no_tracking))
            PromiseRow(stringResource(R.string.onboarding_promise_no_delete))
        }
        PrimaryButton(stringResource(R.string.onboarding_get_started), onClick = onContinue)
    }
}

@Composable
private fun DestinationStep(
    state: OnboardingUiState,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.onboarding_destination_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.onboarding_destination_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                    Icon(painter = painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        val destination = state.destination
                        if (destination != null) {
                            Text(destination.label(stringResource(R.string.storage_internal), stringResource(R.string.storage_removable)), style = MaterialTheme.typography.titleMedium)
                            if (destination.isRemovable) {
                                Text(stringResource(R.string.destination_removable_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(stringResource(R.string.destination_none), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (state.isDestinationSelected) {
                    SecondaryButton(stringResource(R.string.destination_change), onClick = onChooseFolder)
                } else {
                    PrimaryButton(stringResource(R.string.destination_choose), onClick = onChooseFolder)
                }
            }
        }
        Text(stringResource(R.string.destination_sd_guidance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton(stringResource(R.string.onboarding_done), onClick = onDone, enabled = state.isDestinationSelected)
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.onboarding_back))
            }
        }
    }
}
