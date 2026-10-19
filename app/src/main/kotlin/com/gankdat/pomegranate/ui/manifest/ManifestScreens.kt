package com.gankdat.pomegranate.ui.manifest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.trust.ExportResult
import com.gankdat.pomegranate.trust.ManifestFormat
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters

/** "Safe to clean up" summary with exports. */
@Composable
internal fun ProofOfCopyScreen(
    onViewManifest: () -> Unit,
    onCleanupGuidance: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManifestViewModel = viewModel(factory = appViewModelFactory { ManifestViewModel(it.manifest) }),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.exportResults.collect { result ->
            snackbar.showSnackbar(
                when (result) {
                    is ExportResult.Written -> resources.getString(R.string.manifest_written, result.relativePath)
                    ExportResult.NothingToExport -> resources.getString(R.string.manifest_nothing)
                    is ExportResult.Failed -> resources.getString(R.string.manifest_failed, result.message)
                },
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_state_both), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.proof_title), style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(pluralStringResource(R.plurals.proof_count, summary.verifiedCount, summary.verifiedCount), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.proof_size, Formatters.fileSize(summary.totalSizeBytes)), style = MaterialTheme.typography.bodyLarge)
                    summary.lastPulledAtMillis?.let {
                        Text(stringResource(R.string.proof_last, Formatters.dateTime(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(stringResource(R.string.proof_warning), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.export(ManifestFormat.CSV) }, enabled = !exporting && summary.verifiedCount > 0) { Text(stringResource(R.string.manifest_export_csv)) }
                FilledTonalButton(onClick = { viewModel.export(ManifestFormat.JSON) }, enabled = !exporting && summary.verifiedCount > 0) { Text(stringResource(R.string.manifest_export_json)) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onViewManifest, enabled = summary.verifiedCount > 0) { Text(stringResource(R.string.manifest_view)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCleanupGuidance) { Text(stringResource(R.string.title_cleanup_guidance)) }
        }
        SnackbarHost(hostState = snackbar)
    }
}

/** Every verified copy, searchable by filename, path or hash. */
@Composable
internal fun ManifestViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: ManifestViewModel = viewModel(factory = appViewModelFactory { ManifestViewModel(it.manifest) }),
) {
    val records by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.manifest_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(records, key = { it.sourceId + it.destinationPath }) { record ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_state_both), contentDescription = stringResource(R.string.manifest_verified), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(record.originalFilename, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(record.destinationPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${Formatters.fileSize(record.sizeBytes)} • ${fidelityLabel(record.fidelity)} • ${Formatters.dateTime(record.pulledAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(record.sha256.take(16) + "…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun fidelityLabel(fidelity: Fidelity): String = stringResource(
    when (fidelity) {
        Fidelity.ORIGINAL -> R.string.fidelity_label_original
        Fidelity.GPS_STRIPPED -> R.string.fidelity_label_gps_stripped
        Fidelity.TRANSCODED -> R.string.fidelity_label_transcoded
    },
)
