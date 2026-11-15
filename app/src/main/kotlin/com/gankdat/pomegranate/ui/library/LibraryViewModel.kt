package com.gankdat.pomegranate.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.device.DeviceScannerRepository
import com.gankdat.pomegranate.census.device.ScanResult
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.reconcile.ReconciliationResult
import com.gankdat.pomegranate.reconcile.StateIndex
import com.gankdat.pomegranate.reconcile.origin.OriginRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One-off messages for the screen's snackbar. */
internal sealed interface LibraryMessage {
    data class ScanFinished(val result: ScanResult) : LibraryMessage

    data object ScanFailed : LibraryMessage

    data class ReconcileFinished(val result: ReconciliationResult) : LibraryMessage
}

internal class LibraryViewModel(
    private val deviceDao: DeviceItemDao,
    cloudDao: CloudItemDao,
    private val scanner: DeviceScannerRepository,
    private val reconciliation: ReconciliationRepository,
    cloudScanner: CloudScannerRepository,
    auth: AuthRepository,
    private val origins: OriginRepository,
    jobDao: TransferJobDao,
    private val browseSession: BrowseSession,
    private val pullSelection: PullSelection,
    /** Shared with the Home screen so the first look through the phone runs once, not twice. */
    private val censusGate: Mutex = Mutex(),
) : ViewModel() {
    private val _groupingMode = MutableStateFlow(GroupingMode.BY_MONTH)
    val groupingMode: StateFlow<GroupingMode> = _groupingMode.asStateFlow()

    private val _filter = MutableStateFlow<MediaState?>(null)
    val filter: StateFlow<MediaState?> = _filter.asStateFlow()

    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isReconciling = MutableStateFlow(false)
    val isReconciling: StateFlow<Boolean> = _isReconciling.asStateFlow()

    private val messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val messageFlow: Flow<LibraryMessage> = messages.receiveAsFlow()

    val connection: StateFlow<ConnectionSummary> =
        combine(auth.isAuthenticated, cloudScanner.coverage()) { connected, coverage ->
            ConnectionSummary(connected, coverage.itemCount, coverage.roundCount)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConnectionSummary(false, 0, 0))

    /** Google media ids with a verified download. */
    private val pulled: Flow<Set<String>> =
        jobDao.observeAll().map { jobs -> jobs.filter { it.state == TransferState.DONE }.map { it.sourceId }.toSet() }.distinctUntilChanged()

    /** Sessions of rounds Google has closed. */
    private val closedSessions: Flow<Set<String>> = cloudScanner.pickerRounds()
        .map { rounds -> rounds.filter { it.state == PickerRoundState.EXPIRED }.map { it.sessionId }.toSet() }
        .distinctUntilChanged()

    /** The visible entries, and how many Google-only items are held back because their round is closed. */
    private val entries: Flow<Pair<List<LibraryEntry>, Int>> =
        combine(deviceDao.observeAll(), cloudDao.observeAll(), reconciliation.observeStates(), pulled, closedSessions) { device, cloud, states, pulledIds, closed ->
            val stranded = strandedIds(cloud, states, pulledIds, closed)
            buildEntries(device, cloud.filter { it.id !in stranded }, states, pulledIds) to stranded.size
        }

    private val _selection = MutableStateFlow<Set<Long>?>(null)

    /** Cloud item ids ticked for pulling; null when the library is not in selection mode. */
    val selection: StateFlow<Set<Long>?> = _selection.asStateFlow()

    init {
        // New verified downloads: rescan (after the media store has had a moment to see the files)
        // so they pair with their phone files and the totals follow without a manual refresh.
        viewModelScope.launch {
            var known: Int? = null
            pulled.collectLatest { ids ->
                val before = known
                known = ids.size
                if (before != null && ids.size > before) {
                    delay(RESCAN_AFTER_PULL_MILLIS)
                    runScan(full = false, announce = false)
                }
            }
        }
    }

    /** Remembers the list the user is browsing so the detail view can swipe through it. */
    fun prepareBrowse(items: List<LibraryEntry>) {
        browseSession.entries = items.map(BrowseRef::of)
    }

    fun startSelection(first: LibraryEntry? = null) {
        _selection.update { current -> (current ?: emptySet()) + listOfNotNull(first?.takeIf { it.isPullable }?.cloudItemId) }
    }

    fun toggleSelected(entry: LibraryEntry) {
        val id = entry.cloudItemId?.takeIf { entry.isPullable } ?: return
        _selection.update { current -> current?.let { if (id in it) it - id else it + id } }
    }

    /** Ticks every pullable entry of [entries], or unticks them all if they already are. */
    fun toggleAll(entries: List<LibraryEntry>) {
        val ids = entries.filter { it.isPullable }.mapNotNull { it.cloudItemId }.toSet()
        if (ids.isEmpty()) return
        _selection.update { current -> current?.let { if (it.containsAll(ids)) it - ids else it + ids } }
    }

    fun clearSelection() {
        _selection.value = null
    }

    /** Hands the ticked items to the pull screen and leaves selection mode. */
    fun commitSelection() {
        pullSelection.set(_selection.value.orEmpty())
        _selection.value = null
    }

    val libraryState: StateFlow<LibraryUiState> =
        combine(entries, _groupingMode, _filter, scanner.lastScanTime()) { (all, stranded), mode, filter, lastScan ->
            if (all.isEmpty() && stranded == 0) {
                LibraryUiState.Empty(hasScanned = lastScan > 0)
            } else {
                val visible = if (filter == null) all else all.filter { it.state == filter }
                LibraryUiState.Success(
                    groups = GroupingEngine.group(visible, mode),
                    totals = totals(all),
                    filter = filter,
                    lastScanTime = lastScan.takeIf { it > 0 },
                    strandedCount = stranded,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState.Loading)

    fun setGroupingMode(mode: GroupingMode) {
        _groupingMode.value = mode
    }

    fun setFilter(state: MediaState?) {
        _filter.value = state
    }

    fun toggleExpanded(key: String) {
        _expandedKeys.update { if (key in it) it - key else it + key }
    }

    /** Runs a full census before the first use and once after an update that changes the census rules. */
    fun ensureScanned() {
        viewModelScope.launch {
            censusGate.withLock {
                if (scanner.needsFullScan()) runScan(full = true)
            }
        }
    }

    /** Pull-to-refresh: incremental rescan followed by reconciliation. Ignored while a scan is already running. */
    fun refresh() {
        viewModelScope.launch { runScan(full = false) }
    }

    /** Re-runs matching against the current indexes. */
    fun reconcile() {
        viewModelScope.launch { runReconcile(announce = true) }
    }

    private suspend fun runScan(full: Boolean, announce: Boolean = true) {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        try {
            val result = if (full) scanner.scanAll() else scanner.scanIncremental()
            if (announce) messages.send(LibraryMessage.ScanFinished(result))
            runReconcile(announce = false)
        } catch (e: IllegalStateException) {
            if (announce) messages.send(LibraryMessage.ScanFailed)
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun runReconcile(announce: Boolean) {
        if (!_isReconciling.compareAndSet(expect = false, update = true)) return
        try {
            val result = reconciliation.reconcile()
            origins.refresh()
            if (announce) messages.send(LibraryMessage.ReconcileFinished(result))
        } finally {
            _isReconciling.value = false
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RESCAN_AFTER_PULL_MILLIS = 3_000L

        /**
         * Device rows carry their reconciled state; cloud rows appear only when nothing on the device matches them.
         * A cloud row whose download is verified ([pulled], by Google media id) already counts as "on both": its
         * copy exists even if the census has not seen it yet, or never will (a USB drive is outside the media store).
         */
        fun buildEntries(device: List<DeviceItemEntity>, cloud: List<CloudItemEntity>, states: StateIndex, pulled: Set<String> = emptySet()): List<LibraryEntry> {
            val deviceEntries = device.map { LibraryEntry.fromDevice(it, states.deviceStates[it.id] ?: MediaState.DEVICE_ONLY) }
            val cloudEntries = cloud.filter { (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) == MediaState.CLOUD_ONLY }
                .map { LibraryEntry.fromCloud(it, if (it.pickerMediaItemId in pulled) MediaState.BOTH else MediaState.CLOUD_ONLY) }
            return deviceEntries + cloudEntries
        }

        /**
         * Google-only items that can be neither previewed nor downloaded because Google closed their round.
         * They stay in the index (it is what tells the user which dates to pick again) but out of the grid.
         * Items matched to a phone file or already downloaded are not stranded: nothing more is needed from Google.
         */
        fun strandedIds(cloud: List<CloudItemEntity>, states: StateIndex, pulled: Set<String>, closedSessions: Set<String>): Set<Long> =
            if (closedSessions.isEmpty()) {
                emptySet()
            } else {
                cloud.filter { it.sessionId in closedSessions && it.pickerMediaItemId !in pulled && (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) == MediaState.CLOUD_ONLY }
                    .map { it.id }.toSet()
            }

        fun totals(entries: List<LibraryEntry>): Map<MediaState, StateTotals> =
            MediaState.entries.associateWith { state ->
                val members = entries.filter { it.state == state }
                StateTotals(members.size, members.sumOf { it.sizeBytes ?: 0L })
            }
    }
}
