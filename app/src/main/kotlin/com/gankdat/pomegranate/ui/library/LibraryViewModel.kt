package com.gankdat.pomegranate.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.census.device.DeviceScannerRepository
import com.gankdat.pomegranate.census.device.ScanResult
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
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
}

internal class LibraryViewModel(
    private val dao: DeviceItemDao,
    private val scanner: DeviceScannerRepository,
) : ViewModel() {
    private val _groupingMode = MutableStateFlow(GroupingMode.BY_FOLDER)
    val groupingMode: StateFlow<GroupingMode> = _groupingMode.asStateFlow()

    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val messageFlow: Flow<LibraryMessage> = messages.receiveAsFlow()

    val libraryState: StateFlow<LibraryUiState> =
        combine(dao.observeAll(), _groupingMode, scanner.lastScanTime()) { items, mode, lastScan ->
            if (items.isEmpty()) {
                LibraryUiState.Empty(hasScanned = lastScan > 0)
            } else {
                LibraryUiState.Success(
                    groups = GroupingEngine.group(items, mode),
                    totalCount = items.size,
                    totalSizeBytes = items.sumOf { it.size },
                    lastScanTime = lastScan.takeIf { it > 0 },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState.Loading)

    fun setGroupingMode(mode: GroupingMode) {
        _groupingMode.value = mode
    }

    fun toggleExpanded(key: String) {
        _expandedKeys.update { if (key in it) it - key else it + key }
    }

    /** Runs the first full census if none has completed yet; a no-op otherwise. */
    fun ensureScanned() {
        viewModelScope.launch {
            if (scanner.lastScanTime().first() == 0L) runScan(full = true)
        }
    }

    /** Pull-to-refresh: incremental rescan. Ignored while a scan is already running. */
    fun refresh() {
        viewModelScope.launch { runScan(full = false) }
    }

    private suspend fun runScan(full: Boolean) {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return
        try {
            val result = if (full) scanner.scanAll() else scanner.scanIncremental()
            messages.send(LibraryMessage.ScanFinished(result))
        } catch (e: IllegalStateException) {
            messages.send(LibraryMessage.ScanFailed)
        } finally {
            _isRefreshing.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
