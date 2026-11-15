package com.gankdat.pomegranate.ui.library

import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.census.device.contentUri
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.format.MimeLabels
import com.gankdat.pomegranate.ui.navigation.PomegranateScaffold
import com.gankdat.pomegranate.ui.navigation.Screen
import com.gankdat.pomegranate.reconcile.PulledCopies
import com.gankdat.pomegranate.ui.manifest.fidelityLabel
import com.gankdat.pomegranate.ui.navigation.navigateToCloudItemDetail

private val PREVIEW_SIZE = Size(1_600, 1_600)

/**
 * The ViewModel for one device item. With [itemId] it is keyed by that id (the
 * swipeable pager holds one per page); without, the id comes from the
 * navigation arguments.
 */
@Composable
internal fun itemDetailViewModel(itemId: Long? = null): ItemDetailViewModel = viewModel(
    key = itemId?.let { "device:$it" },
    factory = appViewModelFactory { app ->
        ItemDetailViewModel(
            savedStateHandle = createSavedStateHandle().also { handle -> if (itemId != null) handle[Screen.ARG_ITEM_ID] = itemId },
            dao = app.database.deviceItemDao(),
            cloudDao = app.database.cloudItemDao(),
            matchDao = app.database.matchDao(),
            groupDao = app.database.mediaGroupDao(),
            opener = ContentOpener { uri -> app.contentResolver.openInputStream(uri) },
            pulledFidelity = { device ->
                app.database.transferJobDao().getDone().lastOrNull { PulledCopies.isAt(it.relativePath, device) }?.fidelity
            },
        )
    },
)

/** Scaffold plus content for one device item; hosts the ViewModel so the share action can see the item. */
@Composable
internal fun ItemDetailRoute(navController: NavHostController, viewModel: ItemDetailViewModel = itemDetailViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = (state as? ItemDetailUiState.Success)?.item

    PomegranateScaffold(
        navController = navController,
        title = item?.displayName ?: stringResource(R.string.title_item_detail),
        actions = { if (item != null) ShareAction(item) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) { ItemDetailBody(viewModel, navController) }
    }
}

@Composable
internal fun ShareAction(item: DeviceItemEntity) {
    val context = LocalContext.current
    IconButton(onClick = { context.startActivity(shareIntent(item)) }) {
        Icon(painter = painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
    }
}

/** Everything below the top bar for one device item. */
@Composable
internal fun ItemDetailBody(viewModel: ItemDetailViewModel, navController: NavHostController) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        ItemDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ItemDetailUiState.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), style = MaterialTheme.typography.bodyLarge)
        }
        is ItemDetailUiState.Success -> ItemDetailContent(
            state = current,
            onComputeHash = viewModel::computeSha256,
            onOpenCloudItem = current.match?.cloudItem?.let { cloud -> { navController.navigateToCloudItemDetail(cloud.id) } },
        )
    }
}

private fun shareIntent(item: DeviceItemEntity): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, item.contentUri())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun ItemDetailContent(state: ItemDetailUiState.Success, onComputeHash: () -> Unit, onOpenCloudItem: (() -> Unit)?) {
    val item = state.item
    val isVideo = item.mime.startsWith("video/")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        FullSizePreview(ThumbnailSource.Local(item.volume, item.mediaStoreId), item.width, item.height, isVideo)
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusSection(state.state)
            DetailTitle(item.dateTaken, item.displayName)
            DetailsFold {
            MetadataRow(stringResource(R.string.meta_folder), item.bucket.ifBlank { stringResource(R.string.unknown_folder) })
            state.pulledFidelity?.let { MetadataRow(stringResource(R.string.meta_pulled_as), fidelityLabel(it)) }
            MetadataRow(stringResource(R.string.meta_path), "${item.volume}/${item.relativePath}${item.displayName}")
            MetadataRow(stringResource(R.string.meta_type), MimeLabels.label(item.mime))
            MetadataRow(stringResource(R.string.meta_size), Formatters.fileSize(item.size))
            MetadataRow(stringResource(R.string.meta_date_taken), Formatters.dateTime(item.dateTaken))
            MetadataRow(stringResource(R.string.meta_date_modified), Formatters.dateTime(item.dateModified))
            if (item.width > 0 && item.height > 0) {
                MetadataRow(stringResource(R.string.meta_dimensions), stringResource(R.string.meta_dimensions_value, item.width, item.height))
            }
            item.durationMs?.let { MetadataRow(stringResource(R.string.meta_duration), Formatters.duration(it)) }
            ExpandableHash(stringResource(R.string.meta_fingerprint), item.fingerprint)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.meta_sha256), style = MaterialTheme.typography.labelLarge)
            when {
                item.sha256 != null -> ExpandableHash(label = null, value = item.sha256)
                state.isComputingHash -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.hash_computing), style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    if (state.hashFailed) {
                        Text(stringResource(R.string.hash_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onComputeHash, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.hash_compute)) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            state.match?.let { MatchSection(it, onOpenCloudItem) } ?: run {
                Text(stringResource(R.string.section_match), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.match_none_device), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            SiblingsSection(state.siblings)
            }
        }
    }
}

@Composable
internal fun FullSizePreview(source: ThumbnailSource, width: Int, height: Int, isVideo: Boolean, onOpenConnection: (() -> Unit)? = null) {
    val loader = rememberThumbnailLoader()
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    // null = loading; a failure is shown as a message with what to do next, never an endless spinner.
    val preview by produceState<PreviewResult?>(null, source, attempt) {
        value = null
        value = loader.loadResult(source, PREVIEW_SIZE)
    }
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.5f, 2.5f))
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when (val result = preview) {
            null -> CircularProgressIndicator()
            is PreviewResult.Ok -> Image(
                bitmap = result.bitmap.asImageBitmap(),
                contentDescription = stringResource(if (isVideo) R.string.preview_video else R.string.preview_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            is PreviewResult.Failed -> PreviewFailureMessage(
                reason = result.reason,
                httpStatus = result.httpStatus,
                isCloud = source is ThumbnailSource.Remote,
                onRetry = { attempt++ },
                onOpenConnection = onOpenConnection,
            )
        }
    }
}

/** Says why there is no preview and offers the one action that fixes it. */
@Composable
private fun PreviewFailureMessage(reason: PreviewFailure, httpStatus: Int?, isCloud: Boolean, onRetry: () -> Unit, onOpenConnection: (() -> Unit)?) {
    val message = when {
        !isCloud -> R.string.preview_failed_device
        reason == PreviewFailure.SIGNED_OUT -> R.string.preview_failed_signed_out
        reason == PreviewFailure.ROUND_CLOSED -> R.string.preview_failed_round_closed
        reason == PreviewFailure.OFFLINE -> R.string.preview_failed_offline
        else -> R.string.preview_failed_cloud
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isCloud && reason == PreviewFailure.OTHER && httpStatus != null) {
            Text(stringResource(R.string.preview_failed_status, httpStatus), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val needsConnection = isCloud && (reason == PreviewFailure.SIGNED_OUT || reason == PreviewFailure.ROUND_CLOSED)
        if (needsConnection && onOpenConnection != null) {
            OutlinedButton(onClick = onOpenConnection, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(if (reason == PreviewFailure.SIGNED_OUT) R.string.preview_action_sign_in else R.string.preview_action_pick_again))
            }
        } else if (isCloud) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.preview_action_retry)) }
        }
    }
}

@Composable
internal fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExpandableHash(label: String?, value: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp),
    ) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (expanded) value else value.take(16) + "…",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
