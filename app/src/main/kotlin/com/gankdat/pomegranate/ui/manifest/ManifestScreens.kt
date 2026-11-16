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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.theme.MediaStateColors
import java.text.NumberFormat
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val hasCopies = summary.verifiedCount > 0
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val green = MediaStateColors.of(MediaState.DEVICE_ONLY)
                        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(green.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = green, modifier = Modifier.size(28.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                pluralStringResource(R.plurals.proof_big, summary.verifiedCount, NumberFormat.getIntegerInstance().format(summary.verifiedCount)),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(stringResource(R.string.proof_sub, Formatters.fileSize(summary.totalSizeBytes)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.proof_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    summary.lastPulledAtMillis?.let {
                        Text(stringResource(R.string.proof_last, Formatters.dateTime(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Text(stringResource(R.string.proof_warning), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            Column {
                ProofRow(stringResource(R.string.manifest_view), enabled = hasCopies, onClick = onViewManifest)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProofRow(stringResource(R.string.title_cleanup_guidance), enabled = true, onClick = onCleanupGuidance)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrimaryButton(stringResource(R.string.manifest_export_csv), onClick = { viewModel.export(ManifestFormat.CSV) }, enabled = !exporting && hasCopies)
                TextButton(onClick = { viewModel.export(ManifestFormat.JSON) }, enabled = !exporting && hasCopies, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manifest_export_json))
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProofRow(title: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(records, key = { it.sourceId + it.destinationPath }) { record ->
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painter = painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.manifest_verified), tint = MediaStateColors.of(MediaState.DEVICE_ONLY), modifier = Modifier.size(18.dp))
                        Text(record.originalFilename, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(record.destinationPath, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${Formatters.fileSize(record.sizeBytes)} · ${fidelityLabel(record.fidelity)} · ${Formatters.dateTime(record.pulledAtMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 20.dp))
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
        Fidelity.MOTION_REBUILT -> R.string.fidelity_label_motion_rebuilt
        Fidelity.MOTION_STILL_ONLY -> R.string.fidelity_label_motion_still_only
        Fidelity.MOTION_KEPT -> R.string.fidelity_label_motion_kept
    },
)
