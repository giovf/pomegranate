package com.gankdat.pomegranate.census.cloud

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudScannerRepositoryImplTest {
    private lateinit var db: PomegranateDatabase
    private val api = FakePickerApi()
    private var now = 1_700_000_000_000L
    private val poller by lazy { SessionPoller(api, clock = { now }, sleep = { now += it }) }
    private val overlaps = mutableMapOf<String, Int>()
    private val repo by lazy { CloudScannerRepositoryImpl(api, poller, db.cloudItemDao(), db.pickerRoundDao(), clock = { now }, recordOverlap = { id, n -> overlaps[id] = n }) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a round is created, polled, indexed with pagination and counted`() = runBlocking {
        val session = repo.startPickerRound()
        assertEquals(PickerRoundState.ACTIVE, db.pickerRoundDao().getBySessionId(session.id)?.state)

        api.items[session.id] = (1..250).map { picked("m$it") }.toMutableList()
        var polls = 0
        api.getSessionHook = { if (++polls == 2) api.sessions[session.id] = api.sessions[session.id]!!.copy(mediaItemsSet = true) }
        assertEquals(SessionState.Complete, repo.pollSession(session.id))

        val count = repo.indexSession(session.id)

        assertEquals(250, count)
        assertEquals(3, api.listCalls)
        assertEquals(PickerApi.DEFAULT_PAGE_SIZE, api.pageSizeUsed)
        val round = db.pickerRoundDao().getBySessionId(session.id)!!
        assertEquals(PickerRoundState.INDEXED, round.state)
        assertEquals(250, round.itemCount)
        assertEquals(CloudCoverage(itemCount = 250, roundCount = 1), repo.coverage().first())
        val item = db.cloudItemDao().getByPickerMediaItemId("m7")!!
        assertEquals("m7.jpg", item.filename)
        assertEquals(now + CloudItemMapper.BASE_URL_LIFETIME_MILLIS, item.baseUrlExpiresAt)
    }

    @Test
    fun `re-picking an item in a later round updates it instead of duplicating`() = runBlocking {
        val first = repo.startPickerRound()
        api.items[first.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/old"))
        repo.indexSession(first.id)

        val second = repo.startPickerRound()
        api.items[second.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/new"), picked("m2"))
        repo.indexSession(second.id)

        assertEquals(2, db.cloudItemDao().count())
        assertEquals(mapOf(first.id to 0, second.id to 1), overlaps)
        val item = db.cloudItemDao().getByPickerMediaItemId("m1")!!
        assertEquals(second.id, item.sessionId)
        assertEquals("https://lh3/new", item.baseUrl)
        assertEquals(CloudCoverage(itemCount = 2, roundCount = 2), repo.coverage().first())
    }

    @Test
    fun `expiring base URLs are refreshed by re-listing the session`() = runBlocking {
        val session = repo.startPickerRound()
        api.items[session.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/v1"))
        repo.indexSession(session.id)
        api.listCalls = 0

        repo.refreshExpiringBaseUrls(session.id, marginMillis = 10 * 60_000)
        assertEquals(0, api.listCalls) // still fresh

        now += 55 * 60_000
        api.items[session.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/v2"))
        repo.refreshExpiringBaseUrls(session.id, marginMillis = 10 * 60_000)

        assertEquals(1, api.listCalls)
        val item = db.cloudItemDao().getByPickerMediaItemId("m1")!!
        assertEquals("https://lh3/v2", item.baseUrl)
        assertEquals(now + CloudItemMapper.BASE_URL_LIFETIME_MILLIS, item.baseUrlExpiresAt)
    }

    @Test
    fun `unconditional refresh re-lists even fresh links`() = runBlocking {
        val session = repo.startPickerRound()
        api.items[session.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/v1"))
        repo.indexSession(session.id)
        api.items[session.id] = mutableListOf(picked("m1", baseUrl = "https://lh3/v2"))

        repo.refreshBaseUrls(session.id)

        assertEquals("https://lh3/v2", db.cloudItemDao().getByPickerMediaItemId("m1")!!.baseUrl)
    }

    @Test
    fun `a session Google no longer has marks the round expired`() = runBlocking {
        val session = repo.startPickerRound()
        api.items[session.id] = mutableListOf(picked("m1"))
        repo.indexSession(session.id)
        api.sessions.remove(session.id)
        now += 2 * 60 * 60_000

        try {
            repo.refreshExpiringBaseUrls(session.id)
        } catch (e: PickerApiException) {
            assertTrue(e.isGone)
        }
        assertEquals(PickerRoundState.EXPIRED, db.pickerRoundDao().getBySessionId(session.id)?.state)

        assertTrue(repo.pollSession(session.id) is SessionState.Error)
    }

    @Test
    fun `timeout while picking expires the round`() = runBlocking {
        val session = repo.startPickerRound()
        api.sessions[session.id] = api.sessions[session.id]!!.copy(timeoutMillis = 2_000, pollIntervalMillis = 1_000)

        assertEquals(SessionState.Timeout, repo.pollSession(session.id))
        assertEquals(PickerRoundState.EXPIRED, db.pickerRoundDao().getBySessionId(session.id)?.state)
    }

    @Test
    fun `an open round is found again, older open rounds and dropped rounds are expired`() = runBlocking {
        val first = repo.startPickerRound()
        now += 1_000
        val second = repo.startPickerRound()

        assertEquals(second.id, repo.openRound()?.id)
        assertEquals(PickerRoundState.EXPIRED, db.pickerRoundDao().getBySessionId(first.id)?.state)

        api.sessions.remove(second.id)
        assertEquals(null, repo.openRound())
        assertEquals(PickerRoundState.EXPIRED, db.pickerRoundDao().getBySessionId(second.id)?.state)
    }

    @Test
    fun `session ranges give each round's count and date span`() = runBlocking {
        val session = repo.startPickerRound()
        api.items[session.id] = mutableListOf(
            picked("m1").copy(createTimeMillis = 1_000),
            picked("m2").copy(createTimeMillis = 5_000),
            picked("m3").copy(createTimeMillis = 0),
        )
        repo.indexSession(session.id)

        assertEquals(listOf(com.gankdat.pomegranate.data.db.entity.SessionRange(session.id, 3, 1_000, 5_000)), repo.sessionRanges().first())
    }

    @Test
    fun `deleting a session that is already gone is not an error`() = runBlocking {
        val session = repo.startPickerRound()
        repo.deleteSession(session.id)
        repo.deleteSession(session.id)
        assertEquals(listOf(session.id, session.id), api.deleted)
    }
}
