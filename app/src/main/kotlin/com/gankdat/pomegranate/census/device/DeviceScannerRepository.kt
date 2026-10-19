package com.gankdat.pomegranate.census.device

import kotlinx.coroutines.flow.Flow

internal interface DeviceScannerRepository {
    /** Re-reads every volume, reconciles the whole `device_item` table and drops vanished rows. */
    suspend fun scanAll(): ScanResult

    /** Processes only items modified since the last completed scan, then drops vanished rows. */
    suspend fun scanIncremental(): ScanResult

    fun observeProgress(): Flow<ScanProgress>

    /** Epoch millis of the last completed scan, 0 if none. */
    fun lastScanTime(): Flow<Long>
}
