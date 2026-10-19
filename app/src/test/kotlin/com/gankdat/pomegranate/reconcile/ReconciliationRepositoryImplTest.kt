package com.gankdat.pomegranate.reconcile

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.model.MediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReconciliationRepositoryImplTest {
    private lateinit var db: PomegranateDatabase
    private val repo by lazy {
        ReconciliationRepositoryImpl(db.cloudItemDao(), db.deviceItemDao(), db.matchDao(), db.mediaGroupDao(), Dispatchers.Unconfined)
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        db.pickerRoundDao().insert(PickerRoundEntity("s", 1))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `reconcile writes matches, groups and states`() = runBlocking {
        db.deviceItemDao().insertAll(listOf(device(1, "DSC_1.NEF"), device(2, "DSC_1.JPG"), device(3, "only_here.jpg", taken = T0 + 99_000_000)))
        db.cloudItemDao().insertAll(listOf(cloud(1, "DSC_1.JPG"), cloud(2, "only_there.jpg", created = T0 + 50_000_000), cloud(3, "IMG_9.HEIC", created = 1), cloud(4, "IMG_9.MOV", created = 1)))

        val result = repo.reconcile()

        assertEquals(1, result.exact)
        assertEquals(0, result.likely + result.ambiguous)
        assertEquals(2, result.groups)
        val states = repo.observeStates().first()
        assertEquals(MediaState.BOTH, states.cloudStates[1])
        assertEquals(MediaState.CLOUD_ONLY, states.cloudStates[2])
        assertEquals(MediaState.BOTH, states.deviceStates[2])
        assertEquals(MediaState.DEVICE_ONLY, states.deviceStates[1])
        assertEquals(MediaState.DEVICE_ONLY, states.deviceStates[3])
        assertEquals(3, states.count(MediaState.CLOUD_ONLY))
        assertEquals(1, states.count(MediaState.BOTH))
        assertEquals(2, states.count(MediaState.DEVICE_ONLY))

        val groups = db.mediaGroupDao().getAllWithItems()
        val raw = groups.single { it.group.kind == MediaGroupKind.RAW_PAIR }
        assertEquals(listOf(1L, 2L), raw.deviceItems.map { it.id }.sorted())
        assertEquals(listOf(1L), raw.cloudItems.map { it.id }) // matched cloud twin joined the device group
        val live = groups.single { it.group.kind == MediaGroupKind.LIVE_PHOTO }
        assertEquals(listOf(3L, 4L), live.cloudItems.map { it.id }.sorted())
    }

    @Test
    fun `reconcile is idempotent and rebuilds after index changes`() = runBlocking {
        db.deviceItemDao().insert(device(1, "a.jpg"))
        db.cloudItemDao().insert(cloud(1, "a.jpg"))
        repo.reconcile()
        repo.reconcile()
        assertEquals(1, db.matchDao().count())

        db.deviceItemDao().delete(db.deviceItemDao().getById(1)!!)
        repo.reconcile()
        assertEquals(0, db.matchDao().count())
        assertEquals(MediaState.CLOUD_ONLY, repo.observeStates().first().cloudStates[1])
    }

    @Test
    fun `cloud hash confirms or removes an ambiguous match and the verdict survives reconciliation`() = runBlocking {
        db.deviceItemDao().insertAll(listOf(device(1, "a.jpg", taken = T0 + 7 * 60_000), device(2, "b.jpg", taken = T0 + 7 * 60_000)))
        db.deviceItemDao().update(db.deviceItemDao().getById(1)!!.copy(sha256 = "AB".repeat(32)))
        db.deviceItemDao().update(db.deviceItemDao().getById(2)!!.copy(sha256 = "cd".repeat(32)))
        db.cloudItemDao().insertAll(listOf(cloud(1, "a.jpg"), cloud(2, "b.jpg")))
        repo.reconcile()
        assertEquals(2, repo.observeAmbiguousMatches().first().size)

        assertEquals(MatchConfidence.EXACT, repo.confirmWithCloudHash(1, "ab".repeat(32)))
        assertNull(repo.confirmWithCloudHash(2, "ef".repeat(32)))

        assertTrue(repo.observeAmbiguousMatches().first().isEmpty())
        val states = repo.observeStates().first()
        assertEquals(MediaState.BOTH, states.cloudStates[1])
        assertEquals(MediaState.CLOUD_ONLY, states.cloudStates[2])
        assertEquals(MatchMethod.HASH_CONFIRMED, Evidence.fromJson(db.matchDao().getForCloudItem(1).single().evidence)?.method)

        repo.reconcile()
        assertEquals(MatchConfidence.EXACT, db.matchDao().getForCloudItem(1).single().confidence)
        assertEquals(MatchConfidence.AMBIGUOUS, db.matchDao().getForCloudItem(2).single().confidence) // re-proposed, still unconfirmed
    }

    @Test
    fun `hash confirmation without a device hash leaves the match untouched`() = runBlocking {
        db.deviceItemDao().insert(device(1, "a.jpg", taken = 0))
        db.cloudItemDao().insert(cloud(1, "a.jpg"))
        repo.reconcile()
        assertEquals(MatchConfidence.AMBIGUOUS, repo.confirmWithCloudHash(1, "00".repeat(32)))
        assertEquals(1, db.matchDao().count())
    }
}
