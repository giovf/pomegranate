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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.components.FastScroller
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
    /** Opens the swipeable detail view at this index of the list the ViewModel has just prepared. */
    onOpenItem: (Int) -> Unit,
    onOpenConnection: () -> Unit,
    onPullSelected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(
        factory = appViewModelFactory {
            LibraryViewModel(
                it.database.deviceItemDao(), it.database.cloudItemDao(), it.deviceScanner, it.reconciliation, it.cloudScanner, it.auth, it.origins,
                it.database.transferJobDao(), it.browseSession, it.pullSelection, it.censusGate,
            )
        },
    ),
) {
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val mode by viewModel.groupingMode.collectAsStateWithLifecycle()
    val expanded by viewModel.expandedKeys.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isReconciling by viewModel.isReconciling.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    BackHandler(enabled = selection != null) { viewModel.clearSelection() }
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
            if (isReconciling) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (permission.allowsScanning) viewModel.refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val current = state) {
                    LibraryUiState.Loading -> Centered { CircularProgressIndicator() }
                    is LibraryUiState.Empty -> EmptyLibrary(permission, isRefreshing, current.hasScanned)
                    is LibraryUiState.Success -> LibraryList(
                        state = current,
                        mode = mode,
                        expandedKeys = expanded,
                        selection = selection,
                        onOpenConnection = onOpenConnection,
                        onFilter = viewModel::setFilter,
                        onSetMode = viewModel::setGroupingMode,
                        onToggle = viewModel::toggleExpanded,
                        onOpenItem = { items, index ->
                            viewModel.prepareBrowse(items)
                            onOpenItem(index)
                        },
                        onStartSelection = viewModel::startSelection,
                        onToggleSelected = viewModel::toggleSelected,
                        onToggleAll = viewModel::toggleAll,
                    )
                }
            }
            selection?.let { ticked ->
                SelectionBar(
                    count = ticked.size,
                    onCancel = viewModel::clearSelection,
                    onPull = {
                        viewModel.commitSelection()
                        onPullSelected()
                    },
                )
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
    selection: Set<Long>?,
    onOpenConnection: () -> Unit,
    onFilter: (MediaState?) -> Unit,
    onSetMode: (GroupingMode) -> Unit,
    onToggle: (String) -> Unit,
    onOpenItem: (List<LibraryEntry>, Int) -> Unit,
    onStartSelection: (LibraryEntry?) -> Unit,
    onToggleSelected: (LibraryEntry) -> Unit,
    onToggleAll: (List<LibraryEntry>) -> Unit,
) {
    // Bars scale against the largest group, so they stay meaningful with several storage volumes.
    val largestGroup = (state.groups.maxOfOrNull { it.totalSizeBytes } ?: 0L).coerceAtLeast(1L).toFloat()
    val volumes = state.groups.flatMap { g -> g.sizeByVolume.map { it.first } }.distinct()
    // Months open as a plain photo grid; folders start closed because their sizes are the point there.
    val isOpen = { group: MediaGroup -> if (mode == GroupingMode.BY_MONTH) group.key !in expandedKeys else group.key in expandedKeys }
    val showLegend = mode == GroupingMode.BY_FOLDER && volumes.isNotEmpty()
    val listState = rememberLazyListState()
    // One label per list row, in the order the rows are emitted below, for the fast scroller's bubble.
    val labels = remember(state.groups, expandedKeys, mode, showLegend, state.strandedCount > 0) {
        buildList<String?> {
            repeat((if (showLegend) 2 else 1) + (if (state.strandedCount > 0) 1 else 0)) { add(null) }
            state.groups.forEach { group ->
                add(group.name)
                if (isOpen(group)) {
                    group.days.forEach { day -> repeat(1 + (day.items.size + GRID_COLUMNS - 1) / GRID_COLUMNS) { add(day.label) } }
                }
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
    // Pinned: switching between phone, Google and both never needs a scroll back to the top.
    StateFilters(state, mode, onFilter, onSetMode)
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        if (showLegend) item(key = "legend") { StorageLegend(volumes) }
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
            val pullable = state.totals[MediaState.CLOUD_ONLY]?.count ?: 0
            if (selection == null && pullable > 0) {
                TextButton(onClick = { onStartSelection(null) }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(R.string.selection_start)) }
            } else if (selection != null) {
                Text(
                    text = stringResource(R.string.selection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (state.filter == MediaState.DEVICE_ONLY) {
                Text(
                    text = stringResource(R.string.device_only_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (state.strandedCount > 0) {
            item(key = "stranded") { StrandedNotice(state.strandedCount, onOpenConnection) }
        }
        state.groups.forEach { group ->
            val isExpanded = isOpen(group)
            item(key = group.key) {
                MediaGroupHeader(
                    name = group.name,
                    itemCount = group.itemCount,
                    totalSizeBytes = group.totalSizeBytes,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggle(group.key) },
                    iconRes = if (group.isCloudOnly) R.drawable.ic_state_cloud else mode.iconRes(),
                    storageBar = if (mode == GroupingMode.BY_FOLDER) group.sizeByVolume.map { (volume, bytes) -> bytes.toFloat() / largestGroup to volumeColor(volume) } else emptyList(),
                    compact = mode == GroupingMode.BY_MONTH,
                )
            }
            if (isExpanded) {
                group.days.forEach { day ->
                    item(key = "${group.key}:day:${day.key}") {
                        DaySeparator(day.label, onToggleAll = if (selection != null && day.items.any { it.isPullable }) ({ onToggleAll(day.items) }) else null)
                    }
                    items(day.items.chunked(GRID_COLUMNS), key = { row -> "${group.key}:${row.first().key}" }) { row ->
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
                                    onClick = {
                                        if (selection != null) onToggleSelected(entry) else onOpenItem(group.items, group.items.indexOf(entry))
                                    },
                                    onLongClick = { if (selection == null) onStartSelection(entry) else onToggleSelected(entry) },
                                    selected = selection?.let { ticked -> entry.cloudItemId in ticked && entry.isPullable },
                                    selectable = entry.isPullable,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
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
    FastScroller(listState = listState, labelFor = { labels.getOrNull(it) }, modifier = Modifier.align(Alignment.CenterEnd))
    }
    }
}

/** Date above a run of thumbnails taken on the same day; in selection mode it can tick the whole day. */
@Composable
private fun DaySeparator(label: String, onToggleAll: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 40.dp, top = if (onToggleAll == null) 12.dp else 0.dp, bottom = if (onToggleAll == null) 4.dp else 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onToggleAll != null) {
            TextButton(onClick = onToggleAll) { Text(stringResource(R.string.selection_day)) }
        }
    }
}

/** Google-only items whose round is closed are not listed; this says so and leads to the dates to pick again. */
@Composable
private fun StrandedNotice(count: Int, onOpenConnection: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Text(pluralStringResource(R.plurals.library_stranded, count, count), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenConnection) { Text(stringResource(R.string.library_stranded_action)) }
        }
    }
}

/** Shown while ticking items: how many, and the way out or on to the pull. */
@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onPull: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            Text(
                text = pluralStringResource(R.plurals.selection_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Button(onClick = onPull, enabled = count > 0) { Text(stringResource(R.string.selection_pull)) }
        }
    }
}

/**
 * Internal storage uses the primary colour; removable storage uses the same
 * hue at the opposite tone (light on a dark theme, dark on a light one), so
 * the two stay distinct whatever palette dynamic colour picks.
 */
@Composable
private fun volumeColor(volume: String): androidx.compose.ui.graphics.Color =
    if (volume == android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inversePrimary

/** Any volume name other than the primary one counts as removable. */
private const val REMOVABLE_VOLUME = "removable"

/** Which colour is which storage; the bars use these, never the three media-state colours. */
@Composable
private fun StorageLegend(volumes: List<String>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.storage_bar_legend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 2.dp)) {
        val hasRemovable = volumes.any { it != android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY }
        LegendDot(volumeColor(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), stringResource(R.string.storage_internal))
        if (hasRemovable) LegendDot(volumeColor(REMOVABLE_VOLUME), stringResource(R.string.storage_removable))
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
private fun StateFilters(state: LibraryUiState.Success, mode: GroupingMode, onFilter: (MediaState?) -> Unit, onSetMode: (GroupingMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = state.filter == null,
                onClick = { onFilter(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            MediaState.entries.forEach { media ->
                // The chip shows a short word; the full meaning is what a screen reader says.
                val full = stringResource(media.labelRes())
                FilterChip(
                    selected = state.filter == media,
                    onClick = { onFilter(if (state.filter == media) null else media) },
                    label = { Text(stringResource(media.shortLabelRes())) },
                    leadingIcon = {
                        Icon(painter = painterResource(media.iconRes()), contentDescription = null, tint = MediaStateColors.of(media), modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.semantics { contentDescription = full },
                )
            }
        }
        // One switch between the photo grid by month and the folder view with its sizes.
        val other = if (mode == GroupingMode.BY_MONTH) GroupingMode.BY_FOLDER else GroupingMode.BY_MONTH
        IconButton(onClick = { onSetMode(other) }) {
            Icon(painter = painterResource(other.iconRes()), contentDescription = stringResource(other.labelRes()), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun MediaState.shortLabelRes(): Int = when (this) {
    MediaState.DEVICE_ONLY -> R.string.filter_phone
    MediaState.CLOUD_ONLY -> R.string.filter_google
    MediaState.BOTH -> R.string.filter_both
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
