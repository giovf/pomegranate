package com.gankdat.pomegranate.ui.guidance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.trust.ManifestRepository
import com.gankdat.pomegranate.trust.ManifestSummary
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.onboarding.DestinationAccess
import com.gankdat.pomegranate.ui.onboarding.DestinationFolders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class GuidanceUiState(
    val summary: ManifestSummary = ManifestSummary(),
    /** Indexed cloud items without a verified copy; zero after Disconnect because the index is gone. */
    val unpulled: Int = 0,
    val destinationInBackupFolder: Boolean = false,
    val confirmedAtMillis: Long = 0,
)

internal class CleanupGuidanceViewModel(
    manifest: ManifestRepository,
    cloudDao: CloudItemDao,
    jobDao: TransferJobDao,
    private val settings: SettingsRepository,
    access: DestinationAccess,
) : ViewModel() {
    val state: StateFlow<GuidanceUiState> = combine(
        manifest.observeSummary(),
        combine(cloudDao.observeAll(), jobDao.observeAll()) { items, jobs ->
            val pulled = jobs.filter { it.state == TransferState.DONE }.map { it.cloudItemId }.toSet()
            items.count { it.id !in pulled }
        },
        settings.destinationRootUri,
        settings.cleanupConfirmedAtMillis,
    ) { summary, unpulled, root, confirmed ->
        val inBackup = root?.let { runCatching { DestinationFolders.describe(access.treeDocumentId(it.toUri())).isBackupFolder }.getOrNull() } ?: false
        GuidanceUiState(summary, unpulled, inBackup, confirmed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceUiState())

    fun confirmCleanup() {
        viewModelScope.launch { settings.setCleanupConfirmedAtMillis(System.currentTimeMillis()) }
    }
}

/** Manual clean-up of Google Photos: the app cannot delete there, so it guides and gates on proof of copy. */
@Composable
internal fun CleanupGuidanceScreen(
    onOpenProof: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CleanupGuidanceViewModel = viewModel(
        factory = appViewModelFactory { app ->
            CleanupGuidanceViewModel(app.manifest, app.database.cloudItemDao(), app.database.transferJobDao(), app.settings, com.gankdat.pomegranate.ui.onboarding.ResolverDestinationAccess(app.contentResolver))
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var acknowledged by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.guidance_precheck_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(pluralStringResource(R.plurals.proof_count, state.summary.verifiedCount, state.summary.verifiedCount), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.proof_size, Formatters.fileSize(state.summary.totalSizeBytes)), style = MaterialTheme.typography.bodyMedium)
                if (state.unpulled > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(pluralStringResource(R.plurals.guidance_unpulled, state.unpulled, state.unpulled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                androidx.compose.material3.TextButton(onClick = onOpenProof) { Text(stringResource(R.string.manifest_view)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(stringResource(R.string.guidance_acknowledge), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.destinationInBackupFolder) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.backup_warning_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.guidance_collision_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        StepList(stringResource(R.string.guidance_delete_title), listOf(R.string.guidance_delete_1, R.string.guidance_delete_2, R.string.guidance_delete_3, R.string.guidance_delete_4, R.string.guidance_delete_5), enabled = acknowledged)
        Spacer(Modifier.height(24.dp))
        StepList(stringResource(R.string.guidance_backup_title), listOf(R.string.guidance_backup_1, R.string.guidance_backup_2, R.string.guidance_backup_3, R.string.guidance_backup_4), enabled = true)
        Text(stringResource(R.string.guidance_backup_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

        Spacer(Modifier.height(24.dp))
        if (state.confirmedAtMillis > 0) {
            Text(stringResource(R.string.guidance_confirmed, Formatters.dateTime(state.confirmedAtMillis)), style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(onClick = viewModel::confirmCleanup, enabled = acknowledged, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guidance_confirm_button))
            }
        }
    }
}

@Composable
private fun StepList(title: String, steps: List<Int>, enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.semantics { heading() })
    if (!enabled) Text(stringResource(R.string.guidance_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    steps.forEachIndexed { index, res ->
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, color = color)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}
