package com.gankdat.pomegranate.ui.settings

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.model.FolderStrategy
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.transfer.SafStorage
import com.gankdat.pomegranate.ui.onboarding.DestinationAccess
import com.gankdat.pomegranate.ui.onboarding.DestinationFolders
import com.gankdat.pomegranate.ui.onboarding.DestinationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class SettingsUiState(
    val destinationUri: String? = null,
    val destination: DestinationInfo? = null,
    val freeBytes: Long? = null,
    val folderStrategy: FolderStrategy = SettingsRepository.DEFAULT_FOLDER_STRATEGY,
    val concurrency: Int = SettingsRepository.DEFAULT_CONCURRENCY,
    val wifiOnly: Boolean = SettingsRepository.DEFAULT_WIFI_ONLY,
    val pauseOnLowBattery: Boolean = SettingsRepository.DEFAULT_PAUSE_ON_LOW_BATTERY,
    val autoDisconnect: Boolean = SettingsRepository.DEFAULT_AUTO_DISCONNECT,
    val fidelityNoticeAcknowledged: Boolean = false,
)

internal class SettingsViewModel(
    private val settings: SettingsRepository,
    private val access: DestinationAccess,
    private val storage: SafStorage,
) : ViewModel() {
    private val freeBytes = MutableStateFlow<Long?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(settings.destinationRootUri, settings.folderStrategy, settings.downloadConcurrency) { uri, strategy, concurrency -> Triple(uri, strategy, concurrency) },
        combine(settings.wifiOnly, settings.pauseOnLowBattery, settings.autoDisconnect, settings.fidelityNoticeAcknowledged) { w, b, a, f -> listOf(w, b, a, f) },
        freeBytes,
    ) { (uri, strategy, concurrency), flags, free ->
        SettingsUiState(
            destinationUri = uri,
            destination = uri?.let { describe(it) },
            freeBytes = free,
            folderStrategy = strategy,
            concurrency = concurrency,
            wifiOnly = flags[0],
            pauseOnLowBattery = flags[1],
            autoDisconnect = flags[2],
            fidelityNoticeAcknowledged = flags[3],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { settings.destinationRootUri.collect { uri -> freeBytes.value = uri?.let { runCatching { storage.freeBytes(it) }.getOrNull() } } }
    }

    private fun describe(uri: String): DestinationInfo? = try {
        DestinationFolders.describe(access.treeDocumentId(uri.toUri()))
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Future pulls go to the new folder; nothing already written is moved. */
    fun changeDestination(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                access.takePersistable(uri)
            } catch (_: SecurityException) {
                onResult(false)
                return@launch
            }
            settings.setDestinationRootUri(uri.toString())
            onResult(true)
        }
    }

    fun setFolderStrategy(strategy: FolderStrategy) = launch { settings.setFolderStrategy(strategy) }

    fun setConcurrency(value: Int) = launch { settings.setDownloadConcurrency(value) }

    fun setWifiOnly(enabled: Boolean) = launch { settings.setWifiOnly(enabled) }

    fun setPauseOnLowBattery(enabled: Boolean) = launch { settings.setPauseOnLowBattery(enabled) }

    fun setAutoDisconnect(enabled: Boolean) = launch { settings.setAutoDisconnect(enabled) }

    fun setShowFidelityNotice(show: Boolean) = launch { settings.setFidelityNoticeAcknowledged(!show) }

    suspend fun hasPulledFiles(): Boolean = settings.destinationRootUri.first() != null

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
