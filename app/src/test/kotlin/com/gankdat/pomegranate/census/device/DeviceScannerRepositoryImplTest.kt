package com.gankdat.pomegranate.census.device

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.settings.SettingsRepositoryImpl
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceScannerRepositoryImplTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: PomegranateDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepositoryImpl
    private val source = FakeMediaStoreSource()
    private var now = 1_700_000_000_000L
    private lateinit var repository: DeviceScannerRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settings = SettingsRepositoryImpl(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") })
        repository = DeviceScannerRepositoryImpl(source, db.deviceItemDao(), settings, ioDispatcher = Dispatchers.Unconfined, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `full scan inserts everything and records the scan time`() = runBlocking {
        source.put(scanned(1), scanned(2), scanned(3, isVideo = true))

        val result = repository.scanAll()

        assertEquals(ScanResult(itemCount = 3, addedCount = 3, updatedCount = 0, unchangedCount = 0, removedCount = 0, skippedVolumes = emptyList(), durationMillis = 0), result)
        assertEquals(3, db.deviceItemDao().count())
        assertEquals(now, settings.lastDeviceScanTimeMillis.first())
        val video = assertNotNull(db.deviceItemDao().getByMediaStoreId("external_primary", 3))
        assertEquals(12_000L, video.durationMs)
        assertFalse(repository.observeProgress().first().isScanning)
        assertEquals(3, repository.observeProgress().first().scannedCount)
    }

    @Test
    fun `rescan keeps row ids, updates changed rows and skips unchanged ones`() = runBlocking {
        source.put(scanned(1), scanned(2))
        repository.scanAll()
        val originalId = assertNotNull(db.deviceItemDao().getByMediaStoreId("external_primary", 2)).id
        db.deviceItemDao().update(assertNotNull(db.deviceItemDao().getById(originalId)).copy(sha256 = "ab".repeat(32)))

        source.put(scanned(2, size = 9_999)) // edited in place: new size, same id
        val result = repository.scanAll()

        assertEquals(1, result.updatedCount)
        assertEquals(1, result.unchangedCount)
        assertEquals(0, result.addedCount)
        val updated = assertNotNull(db.deviceItemDao().getByMediaStoreId("external_primary", 2))
        assertEquals(originalId, updated.id)
        assertEquals(9_999L, updated.size)
        assertNull(updated.sha256) // content changed, so the old hash no longer applies
    }

    @Test
    fun `items missing from MediaStore are removed per volume`() = runBlocking {
        source.put(scanned(1), scanned(2), scanned(1, volume = "sdcard"))
        repository.scanAll()

        source.remove("external_primary", 1)
        val result = repository.scanAll()

        assertEquals(1, result.removedCount)
        assertEquals(listOf("external_primary" to 2L, "sdcard" to 1L), db.deviceItemDao().getAll().map { it.volume to it.mediaStoreId }.sortedBy { it.first })
    }

    @Test
    fun `incremental scan only reads items modified since the last scan but still detects deletions`() = runBlocking {
        source.put(scanned(1, dateModified = 1_000_000_000_000), scanned(2, dateModified = 1_000_000_000_000))
        repository.scanAll()
        now += 3_600_000

        source.put(scanned(3, dateModified = now), scanned(2, dateModified = now, size = 42))
        source.remove("external_primary", 1)
        val result = repository.scanIncremental()

        assertEquals(2, result.itemCount)
        assertEquals(1, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals(1, result.removedCount)
        assertEquals(listOf(2L, 3L), db.deviceItemDao().getMediaStoreIdsForVolume("external_primary").sorted())
        assertEquals(now, settings.lastDeviceScanTimeMillis.first())
        // The modified-after filter is the previous scan time in seconds minus the skew margin.
        assertEquals((now - 3_600_000) / 1_000 - DeviceScannerRepositoryImpl.CLOCK_SKEW_MARGIN_SECONDS, source.lastModifiedAfterSeconds)
    }

    @Test
    fun `incremental scan without a previous scan falls back to a full scan`() = runBlocking {
        source.put(scanned(1))

        val result = repository.scanIncremental()

        assertEquals(1, result.addedCount)
        assertNull(source.lastModifiedAfterSeconds)
    }

    @Test
    fun `batches larger than the chunk size are processed completely`() = runBlocking {
        val count = DeviceScannerRepositoryImpl.BATCH_SIZE * 2 + 17
        source.put(*(1..count).map { scanned(it.toLong()) }.toTypedArray())

        val result = repository.scanAll()

        assertEquals(count, result.addedCount)
        assertEquals(count, db.deviceItemDao().count())
    }

    @Test
    fun `missing date taken falls back to date modified for the stored row`() = runBlocking {
        source.put(scanned(1, dateTaken = 0, dateModified = 1_234_567_890_000))

        repository.scanAll()

        assertEquals(1_234_567_890_000L, assertNotNull(db.deviceItemDao().getByMediaStoreId("external_primary", 1)).dateTaken)
    }

    @Test
    fun `an unreadable volume is skipped and the others still complete`() = runBlocking {
        source.put(scanned(1), scanned(1, volume = "sdcard"))
        source.failing += "sdcard"

        val result = repository.scanAll()

        assertEquals(listOf("sdcard"), result.skippedVolumes)
        assertEquals(1, result.addedCount)
        assertTrue(db.deviceItemDao().getMediaStoreIdsForVolume("sdcard").isEmpty())
    }

    private fun <T : Any> assertNotNull(value: T?): T {
        assertNotNull("expected a value", value)
        return checkNotNull(value)
    }
}

/** In-memory stand-in for MediaStore keyed by (volume, id). */
internal class FakeMediaStoreSource : MediaStoreSource {
    private val items = linkedMapOf<Pair<String, Long>, ScannedMediaItem>()
    val failing = mutableSetOf<String>()
    var lastModifiedAfterSeconds: Long? = null

    fun put(vararg scanned: ScannedMediaItem) {
        scanned.forEach { items[it.volume to it.mediaStoreId] = it }
    }

    fun remove(volume: String, id: Long) {
        items.remove(volume to id)
    }

    override fun volumes(): List<String> = (listOf("external_primary") + items.keys.map { it.first }).distinct()

    override fun count(volume: String, modifiedAfterSeconds: Long?): Int {
        var n = 0
        forEach(volume, modifiedAfterSeconds) { n++ }
        return n
    }

    override fun ids(volume: String): List<Long> {
        failIfBroken(volume)
        return items.values.filter { it.volume == volume }.map { it.mediaStoreId }
    }

    override fun forEach(volume: String, modifiedAfterSeconds: Long?, consumer: (ScannedMediaItem) -> Unit) {
        failIfBroken(volume)
        lastModifiedAfterSeconds = modifiedAfterSeconds
        items.values
            .filter { it.volume == volume }
            .filter { modifiedAfterSeconds == null || it.dateModified / 1_000 > modifiedAfterSeconds }
            .forEach(consumer)
    }

    private fun failIfBroken(volume: String) {
        if (volume in failing) throw IllegalArgumentException("Volume $volume is not mounted")
    }
}
