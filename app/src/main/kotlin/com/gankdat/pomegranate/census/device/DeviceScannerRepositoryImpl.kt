package com.gankdat.pomegranate.census.device

import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reconciles MediaStore with the `device_item` table in batches. Rows are
 * never dropped and re-created for an item that still exists, so the row id
 * that matches and group membership point at stays stable across scans.
 */
internal class DeviceScannerRepositoryImpl(
    private val source: MediaStoreSource,
    private val dao: DeviceItemDao,
    private val settings: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : DeviceScannerRepository {
    private val progress = MutableStateFlow(ScanProgress())
    private val scanLock = Mutex()

    override fun observeProgress(): Flow<ScanProgress> = progress.asStateFlow()

    override fun lastScanTime(): Flow<Long> = settings.lastDeviceScanTimeMillis

    override suspend fun scanAll(): ScanResult = scan(modifiedAfterSeconds = null).also { settings.setCensusFormatVersion(CENSUS_FORMAT_VERSION) }

    override suspend fun needsFullScan(): Boolean =
        settings.lastDeviceScanTimeMillis.first() == 0L || settings.censusFormatVersion.first() < CENSUS_FORMAT_VERSION

    override suspend fun scanIncremental(): ScanResult {
        val last = settings.lastDeviceScanTimeMillis.first()
        if (needsFullScan()) return scanAll()
        // MediaStore's DATE_MODIFIED has one-second resolution; the margin covers clock skew and rounding.
        val since = (last / 1_000 - CLOCK_SKEW_MARGIN_SECONDS).coerceAtLeast(0)
        return scan(modifiedAfterSeconds = since)
    }

    private suspend fun scan(modifiedAfterSeconds: Long?): ScanResult = scanLock.withLock {
        withContext(ioDispatcher) {
            val startedAt = clock()
            val volumes = source.volumes()
            val skipped = mutableListOf<String>()
            val totals = Totals()

            progress.value = ScanProgress(isScanning = true, scannedCount = 0, totalEstimate = volumes.sumOf { safeCount(it, modifiedAfterSeconds, skipped) })
            for (volume in volumes) {
                if (volume in skipped) continue
                try {
                    scanVolume(volume, modifiedAfterSeconds, totals)
                    totals.removed += removeVanished(volume)
                } catch (e: IllegalArgumentException) {
                    skipped += volume // Volume unmounted between discovery and query.
                } catch (e: SecurityException) {
                    skipped += volume // Permission withdrawn mid-scan.
                }
            }

            settings.setLastDeviceScanTimeMillis(startedAt)
            progress.update { it.copy(isScanning = false) }
            ScanResult(
                itemCount = totals.seen,
                addedCount = totals.added,
                updatedCount = totals.updated,
                unchangedCount = totals.unchanged,
                removedCount = totals.removed,
                skippedVolumes = skipped,
                durationMillis = clock() - startedAt,
            )
        }
    }

    private fun safeCount(volume: String, modifiedAfterSeconds: Long?, skipped: MutableList<String>): Int = try {
        source.count(volume, modifiedAfterSeconds)
    } catch (e: IllegalArgumentException) {
        skipped += volume
        0
    } catch (e: SecurityException) {
        skipped += volume
        0
    }

    private suspend fun scanVolume(volume: String, modifiedAfterSeconds: Long?, totals: Totals) {
        val batch = ArrayList<ScannedMediaItem>(BATCH_SIZE)
        // The cursor is consumed synchronously; batches are flushed to Room between reads.
        val pending = mutableListOf<List<ScannedMediaItem>>()
        source.forEach(volume, modifiedAfterSeconds) { item ->
            batch += item
            if (batch.size == BATCH_SIZE) {
                pending += ArrayList(batch)
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) pending += ArrayList(batch)
        for (chunk in pending) reconcileBatch(volume, chunk, totals)
    }

    private suspend fun reconcileBatch(volume: String, items: List<ScannedMediaItem>, totals: Totals) {
        val existing = dao.getByMediaStoreIds(volume, items.map { it.mediaStoreId }).associateBy { it.mediaStoreId }
        val toInsert = mutableListOf<DeviceItemEntity>()
        val toUpdate = mutableListOf<DeviceItemEntity>()
        for (item in items) {
            val fresh = item.toEntity()
            val current = existing[item.mediaStoreId]
            when {
                current == null -> toInsert += fresh
                current.sameAs(fresh) -> totals.unchanged++
                else -> toUpdate += fresh.copy(id = current.id, sha256 = current.sha256.takeIf { current.fingerprint == fresh.fingerprint })
            }
        }
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
        totals.added += toInsert.size
        totals.updated += toUpdate.size
        totals.seen += items.size
        progress.update { it.copy(scannedCount = totals.seen) }
    }

    private suspend fun removeVanished(volume: String): Int {
        val present = source.ids(volume).toHashSet()
        val stale = dao.getMediaStoreIdsForVolume(volume).filterNot { it in present }
        var removed = 0
        for (chunk in stale.chunked(DeviceItemDao.MAX_BIND_ARGS)) removed += dao.deleteByMediaStoreIds(volume, chunk)
        return removed
    }

    private class Totals {
        var seen = 0
        var added = 0
        var updated = 0
        var unchanged = 0
        var removed = 0
    }

    companion object {
        const val BATCH_SIZE: Int = 500

        /** Bump when census rules change so existing installs re-read every file once. 2 = capture-date repair. */
        const val CENSUS_FORMAT_VERSION: Int = 2
        const val CLOCK_SKEW_MARGIN_SECONDS: Long = 60

        internal fun ScannedMediaItem.toEntity(): DeviceItemEntity = DeviceItemEntity(
            mediaStoreId = mediaStoreId,
            volume = volume,
            relativePath = relativePath,
            bucket = bucket,
            displayName = displayName,
            mime = mime,
            size = size,
            dateTaken = if (dateTaken > 0L) dateTaken else dateModified,
            dateModified = dateModified,
            width = width,
            height = height,
            durationMs = durationMs,
            fingerprint = Fingerprint.from(this).hash(),
        )

        /** Everything MediaStore reports, ignoring the row id and the on-demand hash. */
        private fun DeviceItemEntity.sameAs(other: DeviceItemEntity): Boolean =
            copy(id = 0, sha256 = null) == other.copy(id = 0, sha256 = null)
    }
}
