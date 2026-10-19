package com.gankdat.pomegranate.reconcile.origin

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.OriginSource
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.reconcile.cloud
import com.gankdat.pomegranate.reconcile.device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OriginRepositoryImplTest {
    private lateinit var db: PomegranateDatabase
    private val repo by lazy { OriginRepositoryImpl(db.deviceItemDao(), db.cloudItemDao(), db.originRuleDao(), Dispatchers.Unconfined) }

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
    fun `refresh learns from the device and labels cloud items, keeping overrides`() = runBlocking {
        db.deviceItemDao().insertAll((1..8L).map { device(it, "HOL_${it}00.jpg").copy(bucket = "Holiday") })
        db.cloudItemDao().insertAll(
            listOf(
                cloud(1, "HOL_900.jpg"),
                cloud(2, "IMG-20240305-WA0001.jpg"),
                cloud(3, "mystery.jpg"),
                cloud(4, "HOL_901.jpg").copy(userOverrideOrigin = "Trip"),
            ),
        )

        val changed = repo.refresh()

        assertEquals(3, changed) // items 1, 2 and 4; the override on 4 is kept alongside its inference
        assertEquals(listOf("Holiday"), db.originRuleDao().getBySource(OriginSource.LEARNED).map { it.originLabel })
        assertEquals("Holiday", db.cloudItemDao().getById(1)?.inferredOrigin)
        assertEquals("WhatsApp", db.cloudItemDao().getById(2)?.inferredOrigin)
        assertNull(db.cloudItemDao().getById(3)?.inferredOrigin)
        assertEquals("Trip", db.cloudItemDao().getById(4)?.userOverrideOrigin)
        assertEquals("Holiday", db.cloudItemDao().getById(4)?.inferredOrigin)
        assertEquals(0, repo.refresh()) // idempotent
        assertEquals("Holiday", repo.inferOrigin("HOL_1.jpg")?.origin)
    }

    @Test
    fun `known origins combine learned, built-in and overrides, and overrides can be set and cleared`() = runBlocking {
        db.cloudItemDao().insert(cloud(1, "x.jpg"))
        repo.setUserOverride(1, "  Trip  ")
        assertEquals("Trip", db.cloudItemDao().getById(1)?.userOverrideOrigin)

        val known = repo.knownOrigins().first()
        assertTrue("Trip" in known && "WhatsApp" in known && "Camera" in known)
        assertEquals(known, known.sortedWith(String.CASE_INSENSITIVE_ORDER))

        repo.setUserOverride(1, "")
        assertNull(db.cloudItemDao().getById(1)?.userOverrideOrigin)
    }
}
