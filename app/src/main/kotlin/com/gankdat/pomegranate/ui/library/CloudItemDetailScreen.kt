package com.gankdat.pomegranate.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.format.MimeLabels
import com.gankdat.pomegranate.ui.navigation.PomegranateScaffold
import com.gankdat.pomegranate.ui.navigation.navigateToItemDetail

/** Detail for an item that lives (at least) in Google Photos. */
@Composable
internal fun CloudItemDetailRoute(
    navController: NavHostController,
    viewModel: CloudItemDetailViewModel = viewModel(
        factory = appViewModelFactory { app ->
            CloudItemDetailViewModel(createSavedStateHandle(), app.database.cloudItemDao(), app.database.deviceItemDao(), app.database.matchDao(), app.database.mediaGroupDao(), app.origins)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val knownOrigins by viewModel.knownOrigins.collectAsStateWithLifecycle()
    val item = (state as? CloudItemDetailUiState.Success)?.item

    PomegranateScaffold(navController = navController, title = item?.filename ?: stringResource(R.string.title_item_detail)) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val current = state) {
                CloudItemDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                CloudItemDetailUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cloud_item_not_found), style = MaterialTheme.typography.bodyLarge)
                }
                is CloudItemDetailUiState.Success -> CloudItemDetailContent(
                    state = current,
                    onOpenDeviceItem = current.match?.deviceItem?.let { device -> { navController.navigateToItemDetail(device.id) } },
                    onSetOrigin = viewModel::setOriginOverride,
                    knownOrigins = knownOrigins,
                )
            }
        }
    }
}

@Composable
private fun CloudItemDetailContent(
    state: CloudItemDetailUiState.Success,
    onOpenDeviceItem: (() -> Unit)?,
    onSetOrigin: (String?) -> Unit,
    knownOrigins: List<String>,
) {
    val item = state.item
    val isVideo = item.type == "VIDEO" || item.mime.startsWith("video/")
    var editingOrigin by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        FullSizePreview(ThumbnailSource.Remote(item.baseUrl, item.id, item.sessionId), item.width, item.height, isVideo)
        Column(modifier = Modifier.padding(16.dp)) {
            StatusSection(state.state)
            Spacer(Modifier.height(12.dp))
            MetadataRow(stringResource(R.string.meta_type), MimeLabels.label(item.mime))
            MetadataRow(stringResource(R.string.meta_date_taken), Formatters.dateTime(item.createTime))
            if (item.width > 0 && item.height > 0) {
                MetadataRow(stringResource(R.string.meta_dimensions), stringResource(R.string.meta_dimensions_value, item.width, item.height))
            }
            listOfNotNull(item.cameraMake, item.cameraModel).takeIf { it.isNotEmpty() }?.let { MetadataRow(stringResource(R.string.meta_camera), it.joinToString(" ")) }
            MetadataRow(stringResource(R.string.meta_fidelity), stringResource(if (isVideo) R.string.fidelity_transcoded else R.string.fidelity_gps_stripped))

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            OriginSection(item, onEdit = { editingOrigin = true }, onReset = { onSetOrigin(null) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            state.match?.let { MatchSection(it, onOpenDeviceItem) } ?: run {
                Text(stringResource(R.string.section_match), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.match_none_cloud), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SiblingsSection(state.siblings)
        }
    }
    if (editingOrigin) {
        OriginOverrideDialog(
            initial = item.userOverrideOrigin ?: item.inferredOrigin.orEmpty(),
            suggestions = knownOrigins,
            onDismiss = { editingOrigin = false },
            onSave = { onSetOrigin(it); editingOrigin = false },
        )
    }
}

@Composable
private fun OriginSection(item: CloudItemEntity, onEdit: () -> Unit, onReset: () -> Unit) {
    Text(stringResource(R.string.section_origin), style = MaterialTheme.typography.labelLarge)
    val effective = item.userOverrideOrigin ?: item.inferredOrigin
    Text(
        text = effective ?: stringResource(R.string.origin_unknown),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(
            when {
                item.userOverrideOrigin != null -> R.string.origin_source_override
                item.inferredOrigin != null -> R.string.origin_source_inferred
                else -> R.string.origin_source_none
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.origin_change)) }
        if (item.userOverrideOrigin != null) {
            TextButton(onClick = onReset, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.origin_reset)) }
        }
    }
}

@Composable
private fun OriginOverrideDialog(initial: String, suggestions: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.origin_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.origin_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (suggestions.isNotEmpty()) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            FilterChip(selected = text == suggestion, onClick = { text = suggestion }, label = { Text(suggestion) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.origin_dialog_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.origin_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
