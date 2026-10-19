package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity

internal enum class GroupingMode { BY_FOLDER, BY_MONTH }

/** A folder or a month, with its items newest first. */
internal data class MediaGroup(
    /** Stable identity for list keys and expansion state. */
    val key: String,
    val name: String,
    val items: List<DeviceItemEntity>,
) {
    val itemCount: Int get() = items.size
    val totalSizeBytes: Long = items.sumOf { it.size }
}

internal sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing indexed yet: either no scan has run or the phone has no media the app may see. */
    data class Empty(val hasScanned: Boolean) : LibraryUiState

    data class Success(
        val groups: List<MediaGroup>,
        val totalCount: Int,
        val totalSizeBytes: Long,
        /** Epoch millis of the last completed scan, null if none. */
        val lastScanTime: Long?,
    ) : LibraryUiState
}
