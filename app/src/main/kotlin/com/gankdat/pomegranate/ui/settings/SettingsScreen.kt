package com.gankdat.pomegranate.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.BuildConfig
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.FolderStrategy
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.transfer.ResolverSafStorage
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.onboarding.ResolverDestinationAccess

@Composable
internal fun SettingsScreen(
    onOpenConnection: () -> Unit,
    onOpenProof: () -> Unit,
    onOpenCleanupGuide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory { app ->
            SettingsViewModel(app.settings, ResolverDestinationAccess(app.contentResolver), ResolverSafStorage(app.contentResolver))
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmChange by rememberSaveable { mutableStateOf(false) }
    var changeFailed by rememberSaveable { mutableStateOf(false) }
    var infoDialog by rememberSaveable { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.changeDestination(uri) { ok -> changeFailed = !ok }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_storage))
        SettingRow(
            title = stringResource(R.string.settings_destination),
            subtitle = state.destination?.label(stringResource(R.string.storage_internal), stringResource(R.string.storage_removable)) ?: stringResource(R.string.destination_none),
            onClick = { if (state.destinationUri != null) confirmChange = true else picker.launch(null) },
        )
        state.freeBytes?.let {
            Text(stringResource(R.string.settings_free_space, Formatters.fileSize(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }

        SectionHeader(stringResource(R.string.settings_downloads))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.settings_concurrency, state.concurrency), style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = state.concurrency.toFloat(),
                onValueChange = { viewModel.setConcurrency(it.toInt()) },
                valueRange = SettingsRepository.MIN_CONCURRENCY.toFloat()..SettingsRepository.MAX_CONCURRENCY.toFloat(),
                steps = SettingsRepository.MAX_CONCURRENCY - SettingsRepository.MIN_CONCURRENCY - 1,
            )
        }
        SwitchRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_hint), state.wifiOnly, viewModel::setWifiOnly)
        SwitchRow(stringResource(R.string.settings_low_battery), null, state.pauseOnLowBattery, viewModel::setPauseOnLowBattery)

        SectionHeader(stringResource(R.string.settings_behaviour))
        Text(stringResource(R.string.settings_folder_strategy), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        FolderStrategy.entries.forEach { strategy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = state.folderStrategy == strategy, role = Role.RadioButton, onClick = { viewModel.setFolderStrategy(strategy) })
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                RadioButton(selected = state.folderStrategy == strategy, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (strategy == FolderStrategy.BY_ORIGIN) R.string.strategy_by_origin else R.string.strategy_by_month), style = MaterialTheme.typography.bodyMedium)
            }
        }
        SwitchRow(stringResource(R.string.settings_auto_disconnect), stringResource(R.string.settings_auto_disconnect_hint), state.autoDisconnect, viewModel::setAutoDisconnect)
        SwitchRow(stringResource(R.string.settings_show_fidelity), null, !state.fidelityNoticeAcknowledged, viewModel::setShowFidelityNotice)

        SectionHeader(stringResource(R.string.settings_privacy))
        SettingRow(stringResource(R.string.title_connection), stringResource(R.string.settings_connection_hint), onOpenConnection)
        SettingRow(stringResource(R.string.title_proof), null, onOpenProof)
        SettingRow(stringResource(R.string.title_cleanup_guidance), null, onOpenCleanupGuide)
        SettingRow(stringResource(R.string.settings_data_stored), null) { infoDialog = R.string.settings_data_stored_body }

        SectionHeader(stringResource(R.string.settings_about))
        SettingRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME, null)
        SettingRow(stringResource(R.string.settings_what), null) { infoDialog = R.string.settings_what_body }
        SettingRow(stringResource(R.string.settings_privacy_policy), null) { infoDialog = R.string.settings_privacy_policy_body }
        SettingRow(stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_hint), null)
    }

    if (confirmChange) {
        AlertDialog(
            onDismissRequest = { confirmChange = false },
            title = { Text(stringResource(R.string.settings_change_destination_title)) },
            text = { Text(stringResource(R.string.settings_change_destination_body)) },
            confirmButton = { TextButton(onClick = { confirmChange = false; picker.launch(null) }) { Text(stringResource(R.string.destination_choose)) } },
            dismissButton = { TextButton(onClick = { confirmChange = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (changeFailed) {
        AlertDialog(
            onDismissRequest = { changeFailed = false },
            title = { Text(stringResource(R.string.destination_error_title)) },
            text = { Text(stringResource(R.string.destination_error_not_persistable)) },
            confirmButton = { TextButton(onClick = { changeFailed = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    infoDialog?.let { body ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            text = { Text(stringResource(body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { infoDialog = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
