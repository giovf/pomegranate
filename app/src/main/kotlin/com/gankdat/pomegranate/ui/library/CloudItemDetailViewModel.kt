package com.gankdat.pomegranate.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.MediaGroupDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.Evidence
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal sealed interface CloudItemDetailUiState {
    data object Loading : CloudItemDetailUiState

    data object NotFound : CloudItemDetailUiState

    data class Success(
        val item: CloudItemEntity,
        val state: MediaState,
        val match: MatchInfo?,
        val siblings: List<SiblingInfo>,
    ) : CloudItemDetailUiState
}

internal class CloudItemDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val cloudDao: CloudItemDao,
    private val deviceDao: DeviceItemDao,
    private val matchDao: MatchDao,
    private val groupDao: MediaGroupDao,
) : ViewModel() {
    private val itemId: Long = checkNotNull(savedStateHandle[Screen.ARG_CLOUD_ITEM_ID]) { "cloudItemId argument missing" }
    private val _state = MutableStateFlow<CloudItemDetailUiState>(CloudItemDetailUiState.Loading)
    val state: StateFlow<CloudItemDetailUiState> = _state.asStateFlow()

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
        val state = if (match != null && match.confidence in ReconciliationRepository.CONFIRMED) MediaState.BOTH else MediaState.CLOUD_ONLY
        return CloudItemDetailUiState.Success(item, state, match, siblings)
    }

    fun setOriginOverride(origin: String?) {
        viewModelScope.launch {
            cloudDao.setUserOverrideOrigin(itemId, origin?.trim()?.takeIf { it.isNotEmpty() })
            _state.value.let { if (it is CloudItemDetailUiState.Success) _state.value = load(cloudDao.observeAll().first().first { c -> c.id == itemId }) }
        }
    }
}
