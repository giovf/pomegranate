package com.gankdat.pomegranate.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.ConnectionStatusBar
import com.gankdat.pomegranate.ui.components.iconRes
import com.gankdat.pomegranate.ui.components.labelRes
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.permissions.PermissionState
import com.gankdat.pomegranate.ui.permissions.openAppSettings
import com.gankdat.pomegranate.ui.theme.MediaStateColors

private const val GRID_COLUMNS = 4

/**
 * The library grouped by folder or by month, with the three-state filter.
 * Triggers the first census once media access exists; pull down to rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    permission: PermissionState,
    onOpenItem: (LibraryEntry) -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(
        factory = appViewModelFactory {
            LibraryViewModel(it.database.deviceItemDao(), it.database.cloudItemDao(), it.deviceScanner, it.reconciliation, it.cloudScanner, it.auth, it.origins)
        },
    ),
) {
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val mode by viewModel.groupingMode.collectAsStateWithLifecycle()
    val expanded by viewModel.expandedKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isReconciling by viewModel.isReconciling.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(permission.allowsScanning) {
        if (permission.allowsScanning) viewModel.ensureScanned()
    }
    val scanFailed = stringResource(R.string.scan_failed)
    val noNewItems = stringResource(R.string.scan_no_new_items)
    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { message ->
            val text = when (message) {
                is LibraryMessage.ScanFinished -> {
                    val r = message.result
                    val changed = r.addedCount + r.updatedCount + r.removedCount
                    if (changed == 0) noNewItems else resources.getQuantityString(R.plurals.scan_changes, changed, r.addedCount, r.updatedCount, r.removedCount)
                }
                LibraryMessage.ScanFailed -> scanFailed
                is LibraryMessage.ReconcileFinished -> resources.getString(R.string.reconcile_finished, message.result.exact + message.result.likely, message.result.ambiguous)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ConnectionStatusBar(
                isConnected = connection.isConnected,
                cloudItemCount = connection.cloudItemCount,
                roundCount = connection.roundCount,
                onClick = onOpenConnection,
            )
            PrimaryTabRow(selectedTabIndex = mode.ordinal) {
                GroupingMode.entries.forEach { tab ->
                    Tab(
                        selected = mode == tab,
                        onClick = { viewModel.setGroupingMode(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(tab.iconRes()), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(tab.labelRes()), maxLines = 1)
                            }
                        },
                    )
                }
            }
            if (isReconciling) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (permission.allowsScanning) viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val current = state) {
                    LibraryUiState.Loading -> Centered { CircularProgressIndicator() }
                    is LibraryUiState.Empty -> EmptyLibrary(permission, isRefreshing, current.hasScanned)
                    is LibraryUiState.Success -> LibraryList(current, mode, expanded, viewModel::setFilter, viewModel::toggleExpanded, onOpenItem)
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState.Success,
    mode: GroupingMode,
    expandedKeys: Set<String>,
    onFilter: (MediaState?) -> Unit,
    onToggle: (String) -> Unit,
    onOpenItem: (LibraryEntry) -> Unit,
) {
    // Bars scale against the largest group, so they stay meaningful with several storage volumes.
    val largestGroup = (state.groups.maxOfOrNull { it.totalSizeBytes } ?: 0L).coerceAtLeast(1L).toFloat()
    val volumes = state.groups.flatMap { g -> g.sizeByVolume.map { it.first } }.distinct()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "filters") { StateFilters(state, onFilter) }
        if (volumes.isNotEmpty()) item(key = "legend") { StorageLegend(volumes) }
        item(key = "summary") {
            val filtered = state.filter?.let { state.totals[it] }
            val text = if (filtered == null) {
                pluralStringResource(R.plurals.library_summary, state.totalCount, state.totalCount, Formatters.fileSize(state.totalSizeBytes))
            } else if (state.filter == MediaState.CLOUD_ONLY) {
                pluralStringResource(R.plurals.library_summary_cloud, filtered.count, filtered.count)
            } else {
                pluralStringResource(R.plurals.library_summary_filtered, filtered.count, filtered.count, Formatters.fileSize(filtered.sizeBytes))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.filter == MediaState.DEVICE_ONLY) {
                Text(
                    text = stringResource(R.string.device_only_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        state.groups.forEach { group ->
            val isExpanded = group.key in expandedKeys
            item(key = group.key) {
                MediaGroupHeader(
                    name = group.name,
                    itemCount = group.itemCount,
                    totalSizeBytes = group.totalSizeBytes,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggle(group.key) },
                    iconRes = if (group.isCloudOnly) R.drawable.ic_state_cloud else mode.iconRes(),
                    storageBar = group.sizeByVolume.map { (volume, bytes) -> bytes.toFloat() / largestGroup to volumeColor(volume) },
                )
            }
            if (isExpanded) {
                items(group.items.chunked(GRID_COLUMNS), key = { row -> "${group.key}:${row.first().key}" }) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        row.forEach { entry ->
                            MediaThumbnail(
                                source = entry.thumbnail,
                                state = entry.state,
                                isVideo = entry.isVideo,
                                durationMs = entry.durationMs,
                                onClick = { onOpenItem(entry) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item(key = "footer") {
            state.lastScanTime?.let {
                Text(
                    text = stringResource(R.string.last_scanned, Formatters.dateTime(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun volumeColor(volume: String): androidx.compose.ui.graphics.Color =
    if (volume == android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

/** Which colour is which storage; the bars use these, never the three media-state colours. */
@Composable
private fun StorageLegend(volumes: List<String>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.storage_bar_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 2.dp)) {
        val hasRemovable = volumes.any { it != android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY }
        LegendDot(volumeColor(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), stringResource(R.string.storage_internal))
        if (hasRemovable) LegendDot(MaterialTheme.colorScheme.tertiary, stringResource(R.string.storage_removable))
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StateFilters(state: LibraryUiState.Success, onFilter: (MediaState?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = state.filter == null,
            onClick = { onFilter(null) },
            label = { Text(stringResource(R.string.filter_all, state.totalCount)) },
        )
        MediaState.entries.forEach { media ->
            val total = state.totals[media]?.count ?: 0
            FilterChip(
                selected = state.filter == media,
                onClick = { onFilter(if (state.filter == media) null else media) },
                label = { Text("${stringResource(media.labelRes())} ($total)") },
                leadingIcon = {
                    Icon(painter = painterResource(media.iconRes()), contentDescription = null, tint = MediaStateColors.of(media), modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(permission: PermissionState, isRefreshing: Boolean, hasScanned: Boolean) {
    val context = LocalContext.current
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            when {
                isRefreshing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.scanning), style = MaterialTheme.typography.bodyLarge)
                }
                !permission.allowsScanning -> {
                    Text(stringResource(R.string.empty_no_access), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { context.openAppSettings() }) { Text(stringResource(R.string.permission_open_settings)) }
                }
                hasScanned -> Text(stringResource(R.string.empty_no_media), style = MaterialTheme.typography.bodyLarge)
                else -> Text(stringResource(R.string.empty_not_scanned), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun GroupingMode.labelRes(): Int = when (this) {
    GroupingMode.BY_FOLDER -> R.string.tab_by_folder
    GroupingMode.BY_MONTH -> R.string.tab_by_month
}

private fun GroupingMode.iconRes(): Int = when (this) {
    GroupingMode.BY_FOLDER -> R.drawable.ic_folder
    GroupingMode.BY_MONTH -> R.drawable.ic_calendar
}
