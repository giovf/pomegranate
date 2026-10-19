package com.gankdat.pomegranate.census.device

/** One MediaStore row, normalised: timestamps in epoch millis, nulls resolved. */
internal data class ScannedMediaItem(
    val mediaStoreId: Long,
    val volume: String,
    val relativePath: String,
    val bucket: String,
    val displayName: String,
    val mime: String,
    val size: Long,
    /** 0 when MediaStore has no date taken and no EXIF fallback was available. */
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val isVideo: Boolean,
)
