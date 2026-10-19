package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.model.MediaState

internal enum class GroupingMode { BY_FOLDER, BY_MONTH }

/** Where a thumbnail comes from; kept free of Android types so the model is unit-testable. */
internal sealed interface ThumbnailSource {
    data class Local(val volume: String, val mediaStoreId: Long) : ThumbnailSource

    /** Picker base URL; size parameters are appended by the loader. Ids let the loader refresh an expired link. */
    data class Remote(val baseUrl: String, val cloudItemId: Long, val sessionId: String) : ThumbnailSource
}

/**
 * One row of the library: a device file (device-only or matched to the
 * cloud) or a cloud-only item. Matched pairs appear once, as the device file.
 */
internal data class LibraryEntry(
    val key: String,
    val state: MediaState,
    val deviceItemId: Long?,
    val cloudItemId: Long?,
    val displayName: String,
    /** Device folder, or the inferred/overridden origin for cloud-only items; null when unknown. */
    val bucket: String?,
    val dateTaken: Long,
    /** Null for cloud-only items: the Picker API does not report sizes. */
    val sizeBytes: Long?,
    val mime: String,
    val durationMs: Long?,
    val thumbnail: ThumbnailSource,
) {
    val isVideo: Boolean get() = mime.startsWith("video/")

    companion object {
        fun fromDevice(item: DeviceItemEntity, state: MediaState) = LibraryEntry(
            key = "d:${item.id}",
            state = state,
            deviceItemId = item.id,
            cloudItemId = null,
            displayName = item.displayName,
            bucket = item.bucket.ifBlank { null },
            dateTaken = item.dateTaken,
            sizeBytes = item.size,
            mime = item.mime,
            durationMs = item.durationMs,
            thumbnail = ThumbnailSource.Local(item.volume, item.mediaStoreId),
        )

        fun fromCloud(item: CloudItemEntity) = LibraryEntry(
            key = "c:${item.id}",
            state = MediaState.CLOUD_ONLY,
            deviceItemId = null,
            cloudItemId = item.id,
            displayName = item.filename,
            bucket = item.userOverrideOrigin ?: item.inferredOrigin,
            dateTaken = item.createTime,
            sizeBytes = null,
            mime = item.mime,
            durationMs = item.durationMs,
            thumbnail = ThumbnailSource.Remote(item.baseUrl, item.id, item.sessionId),
        )
    }
}

/** A folder or a month, with its entries newest first. */
internal data class MediaGroup(
    /** Stable identity for list keys and expansion state. */
    val key: String,
    val name: String,
    val items: List<LibraryEntry>,
) {
    val itemCount: Int get() = items.size
    val totalSizeBytes: Long = items.sumOf { it.sizeBytes ?: 0L }
}

internal data class StateTotals(val count: Int, val sizeBytes: Long)

internal data class ConnectionSummary(val isConnected: Boolean, val cloudItemCount: Int, val roundCount: Int)

internal sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing indexed on either side: either no scan has run or the phone has no media the app may see. */
    data class Empty(val hasScanned: Boolean) : LibraryUiState

    data class Success(
        val groups: List<MediaGroup>,
        /** Totals per state over the whole library, independent of the active filter. */
        val totals: Map<MediaState, StateTotals>,
        val filter: MediaState?,
        /** Epoch millis of the last completed scan, null if none. */
        val lastScanTime: Long?,
    ) : LibraryUiState {
        val totalCount: Int get() = totals.values.sumOf { it.count }
        val totalSizeBytes: Long get() = totals.values.sumOf { it.sizeBytes }
    }
}
