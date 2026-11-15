package com.gankdat.pomegranate.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.Evidence
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.reconcile.origin.OriginRepository
import com.gankdat.pomegranate.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface CloudItemDetailUiState {
    data object Loading : CloudItemDetailUiState

    data object NotFound : CloudItemDetailUiState

    data class Success(
        val item: CloudItemEntity,
        val state: MediaState,
        val match: MatchInfo?,
        val siblings: List<SiblingInfo>,
        /** What was actually saved when this item was pulled; null if it has not been. */
        val pulledFidelity: Fidelity? = null,
        val pulledPath: String? = null,
    ) : CloudItemDetailUiState
}

internal class CloudItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val cloudDao: CloudItemDao,
    private val deviceDao: DeviceItemDao,
    private val matchDao: MatchDao,
    private val groupDao: MediaGroupDao,
    private val origins: OriginRepository,
    private val jobDao: TransferJobDao,
    private val pullSelection: PullSelection,
) : ViewModel() {
    private val itemId: Long = checkNotNull(savedStateHandle[Screen.ARG_CLOUD_ITEM_ID]) { "cloudItemId argument missing" }
    private val _state = MutableStateFlow<CloudItemDetailUiState>(CloudItemDetailUiState.Loading)
    val state: StateFlow<CloudItemDetailUiState> = _state.asStateFlow()

    val knownOrigins: StateFlow<List<String>> = origins.knownOrigins().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // Re-load whenever the item row changes (for example an origin override).
            cloudDao.observeAll().map { all -> all.firstOrNull { it.id == itemId } }.collect { item ->
                _state.value = if (item == null) CloudItemDetailUiState.NotFound else load(item)
            }
        }
    }

    private suspend fun load(item: CloudItemEntity): CloudItemDetailUiState.Success {
        val match = matchDao.getForCloudItem(itemId).firstOrNull()?.let { m ->
            MatchInfo(m.confidence, Evidence.fromJson(m.evidence), deviceItem = deviceDao.getById(m.deviceItemId), cloudItem = null)
        }
        val siblings = groupDao.getGroupsForCloudItem(itemId).map { g ->
            SiblingInfo(g.group.kind, (g.deviceItems.map { it.displayName } + g.cloudItems.map { it.filename }).distinct().filter { it != item.filename })
        }
        // A verified pulled copy counts as "on both" even before the next scan pairs it with its phone file.
        val pulledJob = jobDao.getForCloudItem(itemId).lastOrNull { it.state == TransferState.DONE && it.sourceId == item.pickerMediaItemId }
        val pulled = pulledJob != null
        val state = if (pulled || match != null && match.confidence in ReconciliationRepository.CONFIRMED) MediaState.BOTH else MediaState.CLOUD_ONLY
        return CloudItemDetailUiState.Success(item, state, match, siblings, pulledJob?.fidelity, pulledJob?.relativePath?.ifBlank { null })
    }

    /** Makes this one item the selection the copy screen starts from. */
    fun selectForCopy() {
        pullSelection.set(setOf(itemId))
    }

    /** The observed cloud row re-emits after the write, so the state refreshes itself. */
    fun setOriginOverride(origin: String?) {
        viewModelScope.launch { origins.setUserOverride(itemId, origin) }
    }
}
