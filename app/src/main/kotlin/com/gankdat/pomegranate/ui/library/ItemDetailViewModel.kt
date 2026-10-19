package com.gankdat.pomegranate.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.census.device.contentUri
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
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

internal sealed interface ItemDetailUiState {
    data object Loading : ItemDetailUiState

    data object NotFound : ItemDetailUiState

    data class Success(
        val item: DeviceItemEntity,
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
    private val opener: ContentOpener,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val itemId: Long = checkNotNull(savedStateHandle[Screen.ARG_ITEM_ID]) { "itemId argument missing" }
    private val _state = MutableStateFlow<ItemDetailUiState>(ItemDetailUiState.Loading)
    val state: StateFlow<ItemDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = dao.getById(itemId)?.let { ItemDetailUiState.Success(it) } ?: ItemDetailUiState.NotFound
        }
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
            val updated = current.item.copy(sha256 = hash)
            dao.update(updated)
            _state.value = ItemDetailUiState.Success(updated)
        }
    }
}
