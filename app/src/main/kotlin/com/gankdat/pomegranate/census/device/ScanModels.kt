package com.gankdat.pomegranate.census.device

/** Outcome of one census run. */
internal data class ScanResult(
    /** Items examined in this run (all items for a full scan, modified ones for an incremental scan). */
    val itemCount: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    /** Items dropped because MediaStore no longer lists them. */
    val removedCount: Int,
    /** Volumes that could not be read (for example an SD card removed mid-scan). */
    val skippedVolumes: List<String>,
    val durationMillis: Long,
)

/** Live progress of the census; [totalEstimate] comes from a count query before rows are read. */
internal data class ScanProgress(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalEstimate: Int = 0,
)
