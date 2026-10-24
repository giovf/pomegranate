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
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.census.device.contentUri
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.format.MimeLabels
import com.gankdat.pomegranate.ui.navigation.PomegranateScaffold
import com.gankdat.pomegranate.ui.navigation.navigateToCloudItemDetail

private val PREVIEW_SIZE = Size(1_600, 1_600)

/** Scaffold plus content for one device item; hosts the ViewModel so the share action can see the item. */
@Composable
internal fun ItemDetailRoute(
    navController: NavHostController,
    viewModel: ItemDetailViewModel = viewModel(
        factory = appViewModelFactory { app ->
            ItemDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                dao = app.database.deviceItemDao(),
                cloudDao = app.database.cloudItemDao(),
                matchDao = app.database.matchDao(),
                groupDao = app.database.mediaGroupDao(),
                opener = ContentOpener { uri -> app.contentResolver.openInputStream(uri) },
            )
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val item = (state as? ItemDetailUiState.Success)?.item

    PomegranateScaffold(
        navController = navController,
        title = item?.displayName ?: stringResource(R.string.title_item_detail),
        actions = {
            if (item != null) {
                IconButton(onClick = { context.startActivity(shareIntent(item)) }) {
                    Icon(painter = painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
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
        Column(modifier = Modifier.padding(16.dp)) {
            StatusSection(state.state)
            Spacer(Modifier.height(12.dp))
            AssistChip(
                onClick = {},
                label = { Text(item.bucket.ifBlank { stringResource(R.string.unknown_folder) }) },
                leadingIcon = { Icon(painter = painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            Spacer(Modifier.height(8.dp))
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

@Composable
internal fun FullSizePreview(source: ThumbnailSource, width: Int, height: Int, isVideo: Boolean) {
    val loader = rememberThumbnailLoader()
    // null = loading; Result failure = could not load (shown as a message, never an endless spinner).
    val bitmap by produceState<Result<Bitmap>?>(null, source) {
        value = loader.load(source, PREVIEW_SIZE)?.let { Result.success(it) } ?: Result.failure(IllegalStateException())
    }
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.5f, 2.5f))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val result = bitmap
        val loaded = result?.getOrNull()
        if (result == null) {
            CircularProgressIndicator()
        } else if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = stringResource(if (isVideo) R.string.preview_video else R.string.preview_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                stringResource(if (source is ThumbnailSource.Remote) R.string.preview_failed_cloud else R.string.preview_failed_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
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
