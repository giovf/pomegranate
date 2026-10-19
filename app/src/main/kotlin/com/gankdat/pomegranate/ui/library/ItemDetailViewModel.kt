package com.gankdat.pomegranate.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.census.device.contentUri
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.Evidence
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.trust.Sha256
import com.gankdat.pomegranate.ui.navigation.Screen
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The other half of a matched pair, as shown on a detail screen. */
internal data class MatchInfo(
    val confidence: MatchConfidence,
    val evidence: Evidence?,
    val deviceItem: DeviceItemEntity?,
    val cloudItem: CloudItemEntity?,
)

internal data class SiblingInfo(val kind: MediaGroupKind, val filenames: List<String>)

internal sealed interface ItemDetailUiState {
    data object Loading : ItemDetailUiState

    data object NotFound : ItemDetailUiState

    data class Success(
        val item: DeviceItemEntity,
        val state: MediaState,
        val match: MatchInfo?,
        val siblings: List<SiblingInfo>,
        val isComputingHash: Boolean = false,
        val hashFailed: Boolean = false,
    ) : ItemDetailUiState
}

/** Opens a content URI for reading; abstracted so hashing is testable without a ContentResolver. */
internal fun interface ContentOpener {
    fun open(uri: Uri): InputStream?
}

internal class ItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val dao: DeviceItemDao,
    private val cloudDao: CloudItemDao,
    private val matchDao: MatchDao,
    private val groupDao: MediaGroupDao,
    private val opener: ContentOpener,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val itemId: Long = checkNotNull(savedStateHandle[Screen.ARG_ITEM_ID]) { "itemId argument missing" }
    private val _state = MutableStateFlow<ItemDetailUiState>(ItemDetailUiState.Loading)
    val state: StateFlow<ItemDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = load() }
    }

    private suspend fun load(): ItemDetailUiState {
        val item = dao.getById(itemId) ?: return ItemDetailUiState.NotFound
        val match = matchDao.getForDeviceItem(itemId).firstOrNull()?.let { m ->
            MatchInfo(m.confidence, Evidence.fromJson(m.evidence), deviceItem = null, cloudItem = cloudDao.getById(m.cloudItemId))
        }
        val siblings = groupDao.getGroupsForDeviceItem(itemId).map { g ->
            SiblingInfo(g.group.kind, (g.deviceItems.map { it.displayName } + g.cloudItems.map { it.filename }).distinct().filter { it != item.displayName })
        }
        val state = if (match != null && match.confidence in ReconciliationRepository.CONFIRMED) MediaState.BOTH else MediaState.DEVICE_ONLY
        return ItemDetailUiState.Success(item, state, match, siblings)
    }

    /** Hashes the file on disk and stores the result; ambiguous matches use it later. */
    fun computeSha256() {
        val current = _state.value as? ItemDetailUiState.Success ?: return
        if (current.isComputingHash) return
        _state.value = current.copy(isComputingHash = true, hashFailed = false)
        viewModelScope.launch {
            val hash = withContext(ioDispatcher) {
                try {
                    opener.open(current.item.contentUri())?.use { Sha256.hex(it) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            if (hash == null) {
                _state.update { (it as? ItemDetailUiState.Success)?.copy(isComputingHash = false, hashFailed = true) ?: it }
                return@launch
            }
            dao.update(current.item.copy(sha256 = hash))
            _state.value = load()
        }
    }
}
