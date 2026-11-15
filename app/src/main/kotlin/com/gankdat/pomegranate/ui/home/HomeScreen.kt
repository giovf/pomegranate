package com.gankdat.pomegranate.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.iconRes
import com.gankdat.pomegranate.ui.permissions.PermissionState
import com.gankdat.pomegranate.ui.theme.MediaStateColors
import java.text.NumberFormat

/**
 * The first screen: where the photos are, the one thing to do next, and the
 * whole move out of Google Photos as four steps.
 */
@Composable
internal fun HomeScreen(
    permission: PermissionState,
    onOpenGooglePhotos: () -> Unit,
    onCopy: () -> Unit,
    onOpenProof: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = appViewModelFactory {
            HomeViewModel(
                it.database.deviceItemDao(), it.database.cloudItemDao(), it.deviceScanner, it.reconciliation, it.cloudScanner, it.auth, it.origins,
                it.database.transferJobDao(), it.censusGate,
            )
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    LaunchedEffect(permission.allowsScanning) {
        if (permission.allowsScanning) viewModel.ensureScanned()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painter = painterResource(R.drawable.ic_mascot), contentDescription = null, modifier = Modifier.size(width = 35.dp, height = 44.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }

        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.isLoaded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.home_where_title))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(MediaState.DEVICE_ONLY, state.phoneOnly, stringResource(R.string.home_stat_phone), Modifier.weight(1f))
                    StatTile(MediaState.CLOUD_ONLY, state.cloudOnly, stringResource(R.string.home_stat_cloud), Modifier.weight(1f))
                    StatTile(MediaState.BOTH, state.both, stringResource(R.string.home_stat_both), Modifier.weight(1f))
                }
            }

            NextStepCard(state = state, onOpenGooglePhotos = onOpenGooglePhotos, onCopy = onCopy, onOpenProof = onOpenProof)

            if (state.needChoosingAgain > 0 && state.step != JourneyStep.CONNECT) {
                ChooseAgainNotice(count = state.needChoosingAgain, onClick = onOpenGooglePhotos)
            }

            Journey(state = state, onOpenGooglePhotos = onOpenGooglePhotos, onCopy = onCopy, onOpenProof = onOpenProof)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
}

private fun count(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** One of the three places a photo can be, with its fixed colour and icon. */
@Composable
private fun StatTile(place: MediaState, value: Int, label: String, modifier: Modifier = Modifier) {
    val number = count(value)
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$number, $label" },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painter = painterResource(place.iconRes()), contentDescription = null, tint = MediaStateColors.of(place), modifier = Modifier.size(22.dp))
            Text(number, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The single prominent action for where the user is in the move. */
@Composable
private fun NextStepCard(state: HomeUiState, onOpenGooglePhotos: () -> Unit, onCopy: () -> Unit, onOpenProof: () -> Unit) {
    val title: String
    val body: String
    val action: String
    val onAction: () -> Unit
    when (state.step) {
        JourneyStep.CONNECT -> {
            title = stringResource(if (state.hasChosen) R.string.home_reconnect_title else R.string.home_connect_title)
            body = stringResource(if (state.hasChosen) R.string.home_reconnect_body else R.string.home_connect_body)
            action = stringResource(if (state.hasChosen) R.string.connection_sign_in_again else R.string.connection_sign_in)
            onAction = onOpenGooglePhotos
        }
        JourneyStep.CHOOSE -> {
            title = stringResource(R.string.home_choose_title)
            body = stringResource(R.string.home_choose_body)
            action = stringResource(R.string.home_choose_action)
            onAction = onOpenGooglePhotos
        }
        JourneyStep.COPY -> {
            title = pluralStringResource(R.plurals.home_copy_title, state.waitingToCopy, count(state.waitingToCopy))
            body = stringResource(R.string.home_copy_body)
            action = stringResource(R.string.home_copy_action)
            onAction = onCopy
        }
        JourneyStep.COPYING -> {
            title = pluralStringResource(R.plurals.home_copying_title, state.copying, count(state.copying))
            body = stringResource(R.string.home_copying_body)
            action = stringResource(R.string.home_copying_action)
            onAction = onCopy
        }
        JourneyStep.DONE -> {
            title = stringResource(R.string.home_done_title)
            body = stringResource(R.string.home_done_body)
            action = stringResource(R.string.home_done_action)
            onAction = onOpenGooglePhotos
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyLarge)
            }
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(action, style = MaterialTheme.typography.titleMedium)
            }
            if (state.step == JourneyStep.DONE) {
                TextButton(
                    onClick = onOpenProof,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(stringResource(R.string.home_done_proof), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Shown when Google closed a batch before its items were copied: they can only be copied after choosing them again. */
@Composable
private fun ChooseAgainNotice(count: Int, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pluralStringResource(R.plurals.home_again_title, count, count(count)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_again_body), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

private enum class StepStatus { DONE, CURRENT, UPCOMING }

/** The whole move as four steps, so the user always knows what is behind and what is ahead. */
@Composable
private fun Journey(state: HomeUiState, onOpenGooglePhotos: () -> Unit, onCopy: () -> Unit, onOpenProof: () -> Unit) {
    val step = state.step
    Column {
        SectionLabel(stringResource(R.string.home_steps_title))
        JourneyRow(
            number = 1,
            title = stringResource(R.string.home_step_connect),
            subtitle = stringResource(if (state.isConnected) R.string.connection_signed_in else R.string.home_step_connect_todo),
            status = if (state.isConnected) StepStatus.DONE else StepStatus.CURRENT,
            onClick = onOpenGooglePhotos,
        )
        JourneyRow(
            number = 2,
            title = stringResource(R.string.home_step_choose),
            subtitle = if (state.hasChosen) pluralStringResource(R.plurals.home_step_choose_done, state.chosen, count(state.chosen)) else stringResource(R.string.home_step_choose_todo),
            status = when {
                state.hasChosen -> StepStatus.DONE
                step == JourneyStep.CHOOSE -> StepStatus.CURRENT
                else -> StepStatus.UPCOMING
            },
            onClick = if (state.isConnected) onOpenGooglePhotos else null,
        )
        JourneyRow(
            number = 3,
            title = stringResource(R.string.home_step_copy),
            subtitle = when {
                state.copied > 0 -> pluralStringResource(R.plurals.home_step_copy_done, state.copied, count(state.copied))
                step == JourneyStep.COPY || step == JourneyStep.COPYING -> stringResource(R.string.home_step_next)
                else -> stringResource(R.string.home_step_copy_todo)
            },
            status = when {
                state.isCopyFinished -> StepStatus.DONE
                step == JourneyStep.COPY || step == JourneyStep.COPYING -> StepStatus.CURRENT
                else -> StepStatus.UPCOMING
            },
            onClick = if (state.hasChosen) onCopy else null,
        )
        JourneyRow(
            number = 4,
            title = stringResource(R.string.home_step_finish),
            subtitle = stringResource(if (state.isCopyFinished) R.string.home_step_finish_ready else R.string.home_step_finish_todo),
            status = if (state.isCopyFinished) StepStatus.CURRENT else StepStatus.UPCOMING,
            onClick = if (state.copied > 0) onOpenProof else null,
        )
    }
}

@Composable
private fun JourneyRow(number: Int, title: String, subtitle: String, status: StepStatus, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val badge = Modifier.size(28.dp).clip(CircleShape)
        when (status) {
            StepStatus.DONE -> Box(modifier = badge.background(scheme.primary), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.step_done), tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            StepStatus.CURRENT -> Box(modifier = badge.border(2.5.dp, scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            StepStatus.UPCOMING -> Box(modifier = badge.border(2.dp, scheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (status == StepStatus.UPCOMING) scheme.onSurfaceVariant else scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (status == StepStatus.CURRENT) scheme.primary else scheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
