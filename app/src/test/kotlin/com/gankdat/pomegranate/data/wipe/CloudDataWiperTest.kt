package com.gankdat.pomegranate.data.wipe

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.reconcile.cloud
import com.gankdat.pomegranate.reconcile.device
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CloudDataWiperTest {
    private lateinit var db: PomegranateDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).setJournalMode(RoomDatabase.JournalMode.TRUNCATE).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `wipes cloud data, keeps census, proof of copy and rules`() = runBlocking {
        db.pickerRoundDao().insert(PickerRoundEntity("s", 1))
        db.cloudItemDao().insertAll(listOf(cloud(1, "a.jpg"), cloud(2, "b.jpg")))
        db.deviceItemDao().insert(device(1, "a.jpg"))
        db.matchDao().insert(MatchEntity(1, 1, MatchConfidence.EXACT, "{}"))
        db.originRuleDao().insert(OriginRuleEntity(source = OriginSource.LEARNED, pattern = "x", originLabel = "X", confidence = 1f))
        db.transferJobDao().insertAll(
            listOf(
                TransferJobEntity(cloudItemId = 1, sourceId = "g1", originalFilename = "a.jpg", mime = "image/jpeg", state = TransferState.DONE, relativePath = "p/a.jpg", sha256 = "00", fidelity = Fidelity.GPS_STRIPPED, completedAt = 1),
                TransferJobEntity(cloudItemId = 2, sourceId = "g2", originalFilename = "b.jpg", mime = "image/jpeg", state = TransferState.QUEUED, fidelity = Fidelity.GPS_STRIPPED),
            ),
        )

        val counts = CloudDataWiper(db.cloudItemDao(), db.matchDao(), db.pickerRoundDao(), db.transferJobDao()).wipe()

        assertEquals(WipeCounts(cloudItems = 2, matches = 1, rounds = 1, droppedJobs = 1), counts)
        assertEquals(0, db.cloudItemDao().count())
        assertEquals(0, db.matchDao().count())
        assertEquals(0, db.pickerRoundDao().getAll().size)
        assertEquals(1, db.deviceItemDao().count())
        assertEquals(1, db.originRuleDao().getAll().size)
        assertEquals(listOf(TransferState.DONE), db.transferJobDao().getAll().map { it.state })
    }
}
