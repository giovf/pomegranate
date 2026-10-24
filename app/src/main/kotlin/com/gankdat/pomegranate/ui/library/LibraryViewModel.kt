package com.gankdat.pomegranate.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.device.DeviceScannerRepository
import com.gankdat.pomegranate.census.device.ScanResult
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {
    private val _groupingMode = MutableStateFlow(GroupingMode.BY_FOLDER)
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

    private val entries: Flow<List<LibraryEntry>> =
        combine(deviceDao.observeAll(), cloudDao.observeAll(), reconciliation.observeStates(), ::buildEntries)

    val libraryState: StateFlow<LibraryUiState> =
        combine(entries, _groupingMode, _filter, scanner.lastScanTime()) { all, mode, filter, lastScan ->
            if (all.isEmpty()) {
                LibraryUiState.Empty(hasScanned = lastScan > 0)
            } else {
                val visible = if (filter == null) all else all.filter { it.state == filter }
                LibraryUiState.Success(
                    groups = GroupingEngine.group(visible, mode),
                    totals = totals(all),
                    filter = filter,
                    lastScanTime = lastScan.takeIf { it > 0 },
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
            if (scanner.needsFullScan()) runScan(full = true)
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

    private suspend fun runScan(full: Boolean) {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        try {
            val result = if (full) scanner.scanAll() else scanner.scanIncremental()
            messages.send(LibraryMessage.ScanFinished(result))
            runReconcile(announce = false)
        } catch (e: IllegalStateException) {
            messages.send(LibraryMessage.ScanFailed)
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

        /** Device rows carry their reconciled state; cloud rows appear only when nothing on the device matches them. */
        fun buildEntries(device: List<DeviceItemEntity>, cloud: List<CloudItemEntity>, states: StateIndex): List<LibraryEntry> {
            val deviceEntries = device.map { LibraryEntry.fromDevice(it, states.deviceStates[it.id] ?: MediaState.DEVICE_ONLY) }
            val cloudEntries = cloud.filter { (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) == MediaState.CLOUD_ONLY }.map { LibraryEntry.fromCloud(it) }
            return deviceEntries + cloudEntries
        }

        fun totals(entries: List<LibraryEntry>): Map<MediaState, StateTotals> =
            MediaState.entries.associateWith { state ->
                val members = entries.filter { it.state == state }
                StateTotals(members.size, members.sumOf { it.sizeBytes ?: 0L })
            }
    }
}
