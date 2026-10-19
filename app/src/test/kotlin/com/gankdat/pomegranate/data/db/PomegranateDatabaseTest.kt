package com.gankdat.pomegranate.data.db

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.CloudItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemGroupCrossRef
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.db.entity.MediaGroupEntity
import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Runs the real Room schema and generated DAOs on the host JVM against the
 * bundled SQLite driver. The Android `Context` is a stub (see
 * `isReturnDefaultValues` in the build script); Room only consults it for
 * journal-mode heuristics, which we pin explicitly.
 */
class PomegranateDatabaseTest {
    private lateinit var db: PomegranateDatabase

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    // region device_item

    @Test
    fun `device item round-trips through insert, update and delete`() = runBlocking {
        val dao = db.deviceItemDao()
        val id = dao.insert(deviceItem(mediaStoreId = 1, displayName = "IMG_0001.jpg"))

        val stored = expectRow(dao.getById(id))
        assertEquals("IMG_0001.jpg", stored.displayName)
        assertNull(stored.durationMs)
        assertNull(stored.sha256)

        dao.update(stored.copy(sha256 = "ab".repeat(32)))
        assertEquals("ab".repeat(32), dao.getById(id)?.sha256)

        dao.delete(stored)
        assertNull(dao.getById(id))
        assertEquals(0, dao.count())
    }

    @Test
    fun `device item is unique per volume and MediaStore id`() = runBlocking {
        val dao = db.deviceItemDao()
        dao.insert(deviceItem(mediaStoreId = 7, volume = "external_primary"))
        dao.insert(deviceItem(mediaStoreId = 7, volume = "1234-ABCD"))

        assertThrows<SQLiteException> { dao.insert(deviceItem(mediaStoreId = 7, volume = "external_primary")) }
        assertEquals(2, dao.count())
        assertNotNull(dao.getByMediaStoreId("1234-ABCD", 7))
    }

    @Test
    fun `device item upsert keys on volume and MediaStore id and keeps the row id`() = runBlocking {
        val dao = db.deviceItemDao()
        val id = dao.insert(deviceItem(mediaStoreId = 1, size = 10))

        assertEquals(id, dao.upsert(deviceItem(mediaStoreId = 1, size = 20)))
        val newId = dao.upsert(deviceItem(mediaStoreId = 2, size = 30))

        assertTrue(newId != id)
        assertEquals(2, dao.count())
        assertEquals(20L, dao.getById(id)?.size)
        assertEquals(30L, dao.getById(newId)?.size)
    }

    @Test
    fun `device items can be looked up by fingerprint and bucket`() = runBlocking {
        val dao = db.deviceItemDao()
        dao.insertAll(
            listOf(
                deviceItem(mediaStoreId = 1, bucket = "Camera", fingerprint = "fp-a"),
                deviceItem(mediaStoreId = 2, bucket = "Camera", fingerprint = "fp-b"),
                deviceItem(mediaStoreId = 3, bucket = "Screenshots", fingerprint = "fp-a"),
            ),
        )

        assertEquals(listOf(1L, 3L), dao.getByFingerprint("fp-a").map { it.mediaStoreId }.sorted())
        assertEquals(listOf(1L, 2L), dao.getByBucket("Camera").map { it.mediaStoreId }.sorted())
    }

    @Test
    fun `volume-scoped id lookups and deletions do not cross volumes`() = runBlocking {
        val dao = db.deviceItemDao()
        dao.insertAll(
            listOf(
                deviceItem(mediaStoreId = 1, volume = "external_primary"),
                deviceItem(mediaStoreId = 2, volume = "external_primary"),
                deviceItem(mediaStoreId = 1, volume = "sdcard"),
            ),
        )

        assertEquals(listOf(1L, 2L), dao.getMediaStoreIdsForVolume("external_primary").sorted())
        assertEquals(listOf(2L), dao.getByMediaStoreIds("external_primary", listOf(2, 3)).map { it.mediaStoreId })

        val removed = dao.deleteByMediaStoreIds("external_primary", listOf(1))

        assertEquals(1, removed)
        assertEquals(listOf("external_primary" to 2L, "sdcard" to 1L), dao.getAll().map { it.volume to it.mediaStoreId }.sortedBy { it.first })
    }

    @Test
    fun `observed device items reflect inserts`() = runBlocking {
        val dao = db.deviceItemDao()
        assertTrue(dao.observeAll().first().isEmpty())

        dao.insert(deviceItem(mediaStoreId = 1))

        assertEquals(1, dao.observeAll().first().size)
    }

    // endregion

    // region picker_round and cloud_item

    @Test
    fun `cloud item requires its picker round to exist`() = runBlocking {
        assertThrows<SQLiteException> { db.cloudItemDao().insert(cloudItem("g-1", sessionId = "missing")) }
    }

    @Test
    fun `cloud item is unique per Picker media item id`() = runBlocking {
        insertRound("s-1")
        val dao = db.cloudItemDao()
        val id = dao.insert(cloudItem("g-1", sessionId = "s-1"))

        assertThrows<SQLiteException> { dao.insert(cloudItem("g-1", sessionId = "s-1")) }
        assertEquals(id, dao.getByPickerMediaItemId("g-1")?.id)
        assertEquals(1, dao.count())
    }

    @Test
    fun `cloud item upsert keys on the Picker media item id`() = runBlocking {
        insertRound("s-1")
        insertRound("s-2")
        val dao = db.cloudItemDao()
        val id = dao.insert(cloudItem("g-1", sessionId = "s-1"))

        assertEquals(id, dao.upsert(cloudItem("g-1", sessionId = "s-2", baseUrlExpiresAt = 42)))
        val newId = dao.upsert(cloudItem("g-2", sessionId = "s-2"))

        assertTrue(newId != id)
        assertEquals(2, dao.count())
        val updated = expectRow(dao.getById(id))
        assertEquals("s-2", updated.sessionId)
        assertEquals(42L, updated.baseUrlExpiresAt)
    }

    @Test
    fun `cloud items are listed per session and by base URL expiry`() = runBlocking {
        insertRound("s-1")
        insertRound("s-2")
        val dao = db.cloudItemDao()
        dao.insertAll(
            listOf(
                cloudItem("g-1", sessionId = "s-1", baseUrlExpiresAt = 1_000),
                cloudItem("g-2", sessionId = "s-1", baseUrlExpiresAt = 5_000),
                cloudItem("g-3", sessionId = "s-2", baseUrlExpiresAt = 1_000),
            ),
        )

        assertEquals(listOf("g-1", "g-2"), dao.getBySession("s-1").map { it.pickerMediaItemId }.sorted())
        assertEquals(listOf("g-1", "g-3"), dao.getWithExpiredBaseUrl(nowMillis = 2_000).map { it.pickerMediaItemId }.sorted())

        val stale = expectRow(dao.getByPickerMediaItemId("g-1"))
        dao.updateBaseUrl(stale.id, baseUrl = "https://example.invalid/fresh", expiresAt = 9_000)
        assertEquals(listOf("g-3"), dao.getWithExpiredBaseUrl(nowMillis = 2_000).map { it.pickerMediaItemId })
    }

    @Test
    fun `user origin override can be set and cleared`() = runBlocking {
        insertRound("s-1")
        val dao = db.cloudItemDao()
        val id = dao.insert(cloudItem("g-1", sessionId = "s-1"))

        dao.setUserOverrideOrigin(id, "WhatsApp")
        assertEquals("WhatsApp", dao.getById(id)?.userOverrideOrigin)

        dao.setUserOverrideOrigin(id, null)
        assertNull(dao.getById(id)?.userOverrideOrigin)
    }

    @Test
    fun `picker round state and item count are updated together`() = runBlocking {
        val dao = db.pickerRoundDao()
        dao.insert(PickerRoundEntity(sessionId = "s-1", createdAt = 1))
        dao.insert(PickerRoundEntity(sessionId = "s-2", createdAt = 2))

        dao.setState("s-1", PickerRoundState.INDEXED, itemCount = 42)

        val indexed = dao.getByState(PickerRoundState.INDEXED)
        assertEquals(listOf("s-1"), indexed.map { it.sessionId })
        assertEquals(42, indexed.single().itemCount)
        assertEquals(listOf("s-2"), dao.getByState(PickerRoundState.ACTIVE).map { it.sessionId })
        assertEquals(listOf("s-2", "s-1"), dao.getAll().map { it.sessionId })
    }

    @Test
    fun `deleting a picker round cascades to its cloud items, matches and jobs`() = runBlocking {
        val round = insertRound("s-1")
        val cloudId = db.cloudItemDao().insert(cloudItem("g-1", sessionId = "s-1"))
        val deviceId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 1))
        db.matchDao().insert(MatchEntity(cloudId, deviceId, MatchConfidence.EXACT, evidence = "{}"))
        db.transferJobDao().insert(TransferJobEntity(cloudItemId = cloudId, fidelity = Fidelity.ORIGINAL))

        db.pickerRoundDao().delete(round)

        assertEquals(0, db.cloudItemDao().count())
        assertEquals(0, db.matchDao().count())
        assertTrue(db.transferJobDao().getAll().isEmpty())
        assertEquals(1, db.deviceItemDao().count())
    }

    // endregion

    // region match

    @Test
    fun `matches are keyed by the cloud and device pair and replaced on re-insert`() = runBlocking {
        insertRound("s-1")
        val cloudId = db.cloudItemDao().insert(cloudItem("g-1", sessionId = "s-1"))
        val deviceId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 1))
        val dao = db.matchDao()

        dao.insert(MatchEntity(cloudId, deviceId, MatchConfidence.LIKELY, evidence = """{"size":true}"""))
        dao.insert(MatchEntity(cloudId, deviceId, MatchConfidence.EXACT, evidence = """{"sha256":true}"""))

        assertEquals(1, dao.count())
        val stored = dao.getForCloudItem(cloudId).single()
        assertEquals(MatchConfidence.EXACT, stored.confidence)
        assertEquals("""{"sha256":true}""", stored.evidence)
        assertEquals(stored, dao.getForDeviceItem(deviceId).single())
        assertEquals(listOf(stored), dao.getByConfidence(MatchConfidence.EXACT))
        assertTrue(dao.getByConfidence(MatchConfidence.AMBIGUOUS).isEmpty())
    }

    @Test
    fun `deleting a device item removes its matches but keeps the cloud item`() = runBlocking {
        insertRound("s-1")
        val cloudId = db.cloudItemDao().insert(cloudItem("g-1", sessionId = "s-1"))
        val device = deviceItem(mediaStoreId = 1).let { it.copy(id = db.deviceItemDao().insert(it)) }
        db.matchDao().insert(MatchEntity(cloudId, device.id, MatchConfidence.EXACT, evidence = "{}"))

        db.deviceItemDao().delete(device)

        assertEquals(0, db.matchDao().count())
        assertNotNull(db.cloudItemDao().getById(cloudId))
    }

    // endregion

    // region media_group

    @Test
    fun `media group relation loads device and cloud members through the junction tables`() = runBlocking {
        insertRound("s-1")
        val stillId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 1, displayName = "PXL_0001.jpg"))
        val clipId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 2, displayName = "PXL_0001.mp4"))
        val otherId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 3, displayName = "IMG_9.jpg"))
        val cloudId = db.cloudItemDao().insert(cloudItem("g-1", sessionId = "s-1"))
        val dao = db.mediaGroupDao()

        val groupId = dao.insert(MediaGroupEntity(kind = MediaGroupKind.MOTION_PHOTO))
        dao.addDeviceItem(DeviceItemGroupCrossRef(stillId, groupId))
        dao.addDeviceItem(DeviceItemGroupCrossRef(clipId, groupId))
        dao.addDeviceItem(DeviceItemGroupCrossRef(clipId, groupId)) // duplicate membership is ignored
        dao.addCloudItem(CloudItemGroupCrossRef(cloudId, groupId))

        val loaded = expectRow(dao.getWithItems(groupId))
        assertEquals(MediaGroupKind.MOTION_PHOTO, loaded.group.kind)
        assertEquals(listOf(stillId, clipId), loaded.deviceItems.map { it.id }.sorted())
        assertEquals(listOf(cloudId), loaded.cloudItems.map { it.id })

        assertEquals(listOf(groupId), dao.getGroupsForDeviceItem(clipId).map { it.group.id })
        assertTrue(dao.getGroupsForDeviceItem(otherId).isEmpty())
        assertEquals(listOf(groupId), dao.getGroupsForCloudItem(cloudId).map { it.group.id })
        assertEquals(1, dao.getAllWithItems().size)
    }

    @Test
    fun `membership rows follow their group and their items`() = runBlocking {
        val itemId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 1))
        val dao = db.mediaGroupDao()
        val groupId = dao.insert(MediaGroupEntity(kind = MediaGroupKind.RAW_PAIR))
        dao.addDeviceItem(DeviceItemGroupCrossRef(itemId, groupId))

        db.deviceItemDao().delete(expectRow(db.deviceItemDao().getById(itemId)))
        assertTrue(expectRow(dao.getWithItems(groupId)).deviceItems.isEmpty())

        dao.delete(expectRow(dao.getById(groupId)))
        assertEquals(0, dao.count())
        assertThrows<SQLiteException> { dao.addDeviceItem(DeviceItemGroupCrossRef(itemId, groupId)) }
    }

    // endregion

    // region transfer_job

    @Test
    fun `transfer jobs are dequeued in insertion order and tracked by state`() = runBlocking {
        insertRound("s-1")
        val cloudIds = db.cloudItemDao().insertAll((1..3).map { cloudItem("g-$it", sessionId = "s-1") })
        val dao = db.transferJobDao()
        val jobIds = dao.insertAll(cloudIds.map { TransferJobEntity(cloudItemId = it, fidelity = Fidelity.GPS_STRIPPED) })

        assertEquals(3, dao.countByState(TransferState.QUEUED))
        assertEquals(jobIds.take(2), dao.getNextByState(TransferState.QUEUED, limit = 2).map { it.id })

        dao.setState(jobIds[0], TransferState.RUNNING)
        dao.setBytesWritten(jobIds[0], 512)
        assertEquals(jobIds.drop(1), dao.getByState(TransferState.QUEUED).map { it.id })
        val running = expectRow(dao.getById(jobIds[0]))
        assertEquals(TransferState.RUNNING, running.state)
        assertEquals(512L, running.bytesWritten)

        dao.update(running.copy(state = TransferState.DONE, sha256 = "cd".repeat(32), attempts = 1))
        assertEquals(1, dao.countByState(TransferState.DONE))
        assertEquals(listOf(jobIds[0]), dao.getForCloudItem(cloudIds[0]).map { it.id })
    }

    // endregion

    // region origin_rule

    @Test
    fun `origin rules are ordered by confidence and separable by source`() = runBlocking {
        val dao = db.originRuleDao()
        dao.insertAll(
            listOf(
                OriginRuleEntity(source = OriginSource.BUILTIN, pattern = "^IMG-\\d{8}-WA\\d{4}", originLabel = "WhatsApp", confidence = 0.95f),
                OriginRuleEntity(source = OriginSource.LEARNED, pattern = "^PXL_", originLabel = "Camera", confidence = 0.6f),
                OriginRuleEntity(source = OriginSource.LEARNED, pattern = "^Screenshot_", originLabel = "Screenshots", confidence = 0.99f),
            ),
        )

        assertEquals(listOf("Screenshots", "WhatsApp", "Camera"), dao.getAll().map { it.originLabel })
        assertEquals(listOf("Screenshots", "Camera"), dao.getBySource(OriginSource.LEARNED).map { it.originLabel })

        dao.deleteBySource(OriginSource.LEARNED)
        assertEquals(listOf("WhatsApp"), dao.getAll().map { it.originLabel })
    }

    // endregion

    // region converters

    @Test
    fun `enums are stored as their constant names`() = runBlocking {
        insertRound("s-1")
        val cloudId = db.cloudItemDao().insert(cloudItem("g-1", sessionId = "s-1"))
        val deviceId = db.deviceItemDao().insert(deviceItem(mediaStoreId = 1))
        db.mediaGroupDao().insert(MediaGroupEntity(kind = MediaGroupKind.EDIT_PAIR))
        db.matchDao().insert(MatchEntity(cloudId, deviceId, MatchConfidence.AMBIGUOUS, evidence = "{}"))
        db.transferJobDao().insert(TransferJobEntity(cloudItemId = cloudId, state = TransferState.PAUSED, fidelity = Fidelity.TRANSCODED))
        db.originRuleDao().insert(OriginRuleEntity(source = OriginSource.BUILTIN, pattern = "x", originLabel = "X", confidence = 1f))
        db.pickerRoundDao().setState("s-1", PickerRoundState.EXPIRED, itemCount = 0)

        assertEquals("EDIT_PAIR", rawText("SELECT kind FROM media_group"))
        assertEquals("AMBIGUOUS", rawText("SELECT confidence FROM `match`"))
        assertEquals("PAUSED", rawText("SELECT state FROM transfer_job"))
        assertEquals("TRANSCODED", rawText("SELECT fidelity FROM transfer_job"))
        assertEquals("BUILTIN", rawText("SELECT source FROM origin_rule"))
        assertEquals("EXPIRED", rawText("SELECT state FROM picker_round"))

        // And they come back as the same constants.
        assertEquals(MediaGroupKind.EDIT_PAIR, db.mediaGroupDao().getAllWithItems().single().group.kind)
        assertEquals(MatchConfidence.AMBIGUOUS, db.matchDao().getAll().single().confidence)
        assertEquals(TransferState.PAUSED, db.transferJobDao().getAll().single().state)
        assertEquals(Fidelity.TRANSCODED, db.transferJobDao().getAll().single().fidelity)
        assertEquals(OriginSource.BUILTIN, db.originRuleDao().getAll().single().source)
        assertEquals(PickerRoundState.EXPIRED, db.pickerRoundDao().getBySessionId("s-1")?.state)
    }

    @Test
    fun `converters round-trip every constant of every enum`() {
        val converters = Converters()
        MediaGroupKind.entries.forEach { assertEquals(it, converters.toMediaGroupKind(converters.fromMediaGroupKind(it))) }
        MatchConfidence.entries.forEach { assertEquals(it, converters.toMatchConfidence(converters.fromMatchConfidence(it))) }
        TransferState.entries.forEach { assertEquals(it, converters.toTransferState(converters.fromTransferState(it))) }
        Fidelity.entries.forEach { assertEquals(it, converters.toFidelity(converters.fromFidelity(it))) }
        OriginSource.entries.forEach { assertEquals(it, converters.toOriginSource(converters.fromOriginSource(it))) }
        PickerRoundState.entries.forEach { assertEquals(it, converters.toPickerRoundState(converters.fromPickerRoundState(it))) }
    }

    // endregion

    // region fixtures

    private suspend fun insertRound(sessionId: String): PickerRoundEntity =
        PickerRoundEntity(sessionId = sessionId, createdAt = 1_700_000_000_000).also { db.pickerRoundDao().insert(it) }

    private suspend fun rawText(sql: String): String =
        db.useReaderConnection { connection ->
            connection.usePrepared(sql) { statement ->
                check(statement.step()) { "no row for: $sql" }
                statement.getText(0)
            }
        }

    private fun deviceItem(
        mediaStoreId: Long,
        volume: String = "external_primary",
        bucket: String = "Camera",
        displayName: String = "IMG_$mediaStoreId.jpg",
        size: Long = 1_024,
        fingerprint: String = "fp-$volume-$mediaStoreId",
    ) = DeviceItemEntity(
        mediaStoreId = mediaStoreId,
        volume = volume,
        relativePath = "DCIM/$bucket/",
        bucket = bucket,
        displayName = displayName,
        mime = "image/jpeg",
        size = size,
        dateTaken = 1_700_000_000_000 + mediaStoreId,
        dateModified = 1_700_000_000_000 + mediaStoreId,
        width = 4_000,
        height = 3_000,
        fingerprint = fingerprint,
    )

    private fun cloudItem(
        pickerMediaItemId: String,
        sessionId: String,
        baseUrlExpiresAt: Long = 1_700_000_003_600_000,
    ) = CloudItemEntity(
        pickerMediaItemId = pickerMediaItemId,
        sessionId = sessionId,
        filename = "$pickerMediaItemId.jpg",
        mime = "image/jpeg",
        type = "PHOTO",
        createTime = 1_700_000_000_000,
        width = 4_000,
        height = 3_000,
        baseUrl = "https://example.invalid/$pickerMediaItemId",
        baseUrlExpiresAt = baseUrlExpiresAt,
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (expected: Throwable) {
            if (expected is T) return
            throw expected
        }
        fail("Expected ${T::class.simpleName} to be thrown")
    }

    private fun <T : Any> expectRow(value: T?): T {
        assertNotNull("expected a value", value)
        return checkNotNull(value)
    }

    // endregion
}
