package com.gankdat.pomegranate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.device.DeviceScannerRepository
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.reconcile.origin.OriginRepository
import com.gankdat.pomegranate.ui.library.LibraryViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Feeds the Home screen. The three counts come from the same rules as the
 * Photos grid ([LibraryViewModel.buildEntries]), so the two screens can never
 * disagree about where a photo is.
 */
internal class HomeViewModel(
    deviceDao: DeviceItemDao,
    cloudDao: CloudItemDao,
    private val scanner: DeviceScannerRepository,
    private val reconciliation: ReconciliationRepository,
    cloudScanner: CloudScannerRepository,
    auth: AuthRepository,
    private val origins: OriginRepository,
    jobDao: TransferJobDao,
    /** Shared with the Photos screen so the first look through the phone runs once, not twice. */
    private val censusGate: Mutex,
) : ViewModel() {
    private val _isScanning = MutableStateFlow(false)

    /** True while this screen is looking through the phone for the first time. */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val jobs: Flow<List<TransferJobEntity>> = jobDao.observeAll()

    /** Google media ids with a checked copy. */
    private val copiedIds: Flow<Set<String>> =
        jobs.map { all -> all.filter { it.state == TransferState.DONE }.map { it.sourceId }.toSet() }.distinctUntilChanged()

    private val closedSessions: Flow<Set<String>> = cloudScanner.pickerRounds()
        .map { rounds -> rounds.filter { it.state == PickerRoundState.EXPIRED }.map { it.sessionId }.toSet() }
        .distinctUntilChanged()

    private data class Places(val phoneOnly: Int, val cloudOnly: Int, val both: Int, val waiting: Int, val closed: Int)

    private val places: Flow<Places> =
        combine(deviceDao.observeAll(), cloudDao.observeAll(), reconciliation.observeStates(), copiedIds, closedSessions) { device, cloud, states, copied, closed ->
            val stranded = LibraryViewModel.strandedIds(cloud, states, copied, closed)
            val entries = LibraryViewModel.buildEntries(device, cloud.filter { it.id !in stranded }, states, copied)
            val totals = LibraryViewModel.totals(entries)
            Places(
                phoneOnly = totals[MediaState.DEVICE_ONLY]?.count ?: 0,
                cloudOnly = totals[MediaState.CLOUD_ONLY]?.count ?: 0,
                both = totals[MediaState.BOTH]?.count ?: 0,
                waiting = entries.count { it.isPullable },
                closed = stranded.size,
            )
        }

    val state: StateFlow<HomeUiState> =
        combine(places, auth.isAuthenticated, cloudScanner.coverage(), jobs) { where, connected, coverage, allJobs ->
            HomeUiState(
                isLoaded = true,
                phoneOnly = where.phoneOnly,
                cloudOnly = where.cloudOnly,
                both = where.both,
                isConnected = connected,
                chosen = coverage.itemCount,
                waitingToCopy = where.waiting,
                copying = allJobs.count { it.state in ACTIVE },
                copied = allJobs.count { it.state == TransferState.DONE },
                needChoosingAgain = where.closed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    /** Looks through the phone before first use (and after an update that changes the rules), then matches it with Google Photos. */
    fun ensureScanned() {
        viewModelScope.launch {
            censusGate.withLock {
                if (!scanner.needsFullScan()) return@withLock
                _isScanning.value = true
                try {
                    scanner.scanAll()
                    reconciliation.reconcile()
                    origins.refresh()
                } catch (_: IllegalStateException) {
                    // The scan could not finish; the Photos screen reports it and offers a retry.
                } finally {
                    _isScanning.value = false
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val ACTIVE = setOf(TransferState.QUEUED, TransferState.RUNNING, TransferState.VERIFYING, TransferState.PAUSED)
    }
}
