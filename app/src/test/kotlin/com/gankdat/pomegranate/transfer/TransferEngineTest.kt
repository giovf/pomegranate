package com.gankdat.pomegranate.transfer

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.census.cloud.CloudCoverage
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.PickerApiException
import com.gankdat.pomegranate.census.cloud.PickerSession
import com.gankdat.pomegranate.census.cloud.SessionState
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.model.FolderStrategy
import com.gankdat.pomegranate.data.settings.SettingsRepositoryImpl
import com.gankdat.pomegranate.reconcile.ReconciliationRepositoryImpl
import com.gankdat.pomegranate.reconcile.cloud
import com.gankdat.pomegranate.reconcile.device
import com.gankdat.pomegranate.trust.Sha256
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeCloudScanner(private val onRefresh: suspend (String) -> Unit) : CloudScannerRepository {
    override suspend fun startPickerRound(): PickerSession = error("unused")
    override suspend fun pollSession(sessionId: String): SessionState = error("unused")
    override suspend fun indexSession(sessionId: String): Int = error("unused")
    override suspend fun refreshExpiringBaseUrls(sessionId: String, marginMillis: Long) = onRefresh(sessionId)
    override suspend fun deleteSession(sessionId: String) = Unit
    override fun coverage(): Flow<CloudCoverage> = flowOf(CloudCoverage(0, 0))
    override fun pickerRounds() = flowOf(emptyList<PickerRoundEntity>())
}

class TransferEngineTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: PomegranateDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepositoryImpl
    private val storage = FakeSafStorage()
    private val downloader = FakeDownloader()
    private val registered = mutableListOf<String>()
    private var now = 1_709_649_000_000L
    private val refreshed = mutableListOf<String>()
    private var refreshFailure: Exception? = null
    private val photoBytes = ByteArray(1_000) { it.toByte() }

    private val engine by lazy {
        TransferEngine(
            jobs = db.transferJobDao(),
            cloudItems = db.cloudItemDao(),
            settings = settings,
            cloudScanner = FakeCloudScanner { sessionId ->
                refreshFailure?.let { throw it }
                refreshed += sessionId
                db.cloudItemDao().getAll().forEach { db.cloudItemDao().updateBaseUrl(it.id, it.baseUrl + "-fresh", now + 3_600_000) }
            },
            reconciliation = ReconciliationRepositoryImpl(db.cloudItemDao(), db.deviceItemDao(), db.matchDao(), db.mediaGroupDao(), Dispatchers.Unconfined),
            storage = storage,
            downloader = downloader,
            registrar = { docId, _ -> registered += docId },
            ioDispatcher = Dispatchers.Unconfined,
            clock = { now },
        )
    }
    private val queue by lazy { TransferQueueImpl(db.transferJobDao(), db.cloudItemDao()) }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settings = SettingsRepositoryImpl(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") })
        settings.setDestinationRootUri("tree:root")
        db.pickerRoundDao().insert(PickerRoundEntity("s", 1))
        db.cloudItemDao().insert(cloud(1, "IMG_1.jpg", created = now).copy(inferredOrigin = "Camera", baseUrlExpiresAt = now + 3_600_000))
        downloader.bodies["https://lh3/1=d"] = photoBytes
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    private suspend fun enqueueOne(): Long {
        assertEquals(1, queue.enqueue(listOf(1)))
        return db.transferJobDao().getByState(TransferState.QUEUED).single().id
    }

    @Test
    fun `happy path writes, verifies, registers and confirms the match`() = runBlocking {
        db.deviceItemDao().insert(device(1, "IMG_1.jpg", taken = 0).copy(sha256 = Sha256.hex(photoBytes)))
        ReconciliationRepositoryImpl(db.cloudItemDao(), db.deviceItemDao(), db.matchDao(), db.mediaGroupDao(), Dispatchers.Unconfined).reconcile()
        assertEquals(MatchConfidence.AMBIGUOUS, db.matchDao().getForCloudItem(1).single().confidence)
        val jobId = enqueueOne()

        val state = engine.run(db.transferJobDao().getById(jobId)!!)

        assertEquals(TransferState.DONE, state)
        val job = db.transferJobDao().getById(jobId)!!
        assertEquals("Pomegranate/2024/2024-03/Camera/IMG_1.jpg", job.relativePath)
        assertEquals("Pomegranate/2024/2024-03/Camera/IMG_1.jpg", storage.pathOf(job.destinationUri))
        assertEquals(1_000L, job.bytesWritten)
        assertEquals(1_000L, job.bytesExpected)
        assertEquals(Sha256.hex(photoBytes), job.sha256)
        assertEquals(now, job.completedAt)
        assertEquals(1, job.attempts)
        assertEquals(listOf("https://lh3/1=d"), downloader.requested)
        assertEquals(listOf("primary:IMG_1.jpg"), registered)
        assertTrue(refreshed.isEmpty())
        assertEquals(MatchConfidence.EXACT, db.matchDao().getForCloudItem(1).single().confidence)
        assertEquals(TransferSummary(done = 1, bytesWritten = 1_000, bytesExpected = 1_000), queue.observeSummary().first())
    }

    @Test
    fun `month-only strategy and name collisions`() = runBlocking {
        settings.setFolderStrategy(FolderStrategy.BY_MONTH_ONLY)
        val month = storage.findOrCreateFolder(storage.findOrCreateFolder(storage.findOrCreateFolder("tree:root", "Pomegranate"), "2024"), "2024-03")
        storage.createFile(month, "image/jpeg", "IMG_1.jpg")
        val jobId = enqueueOne()

        engine.run(db.transferJobDao().getById(jobId)!!)

        assertEquals("Pomegranate/2024/2024-03/IMG_1 (1).jpg", db.transferJobDao().getById(jobId)!!.relativePath)
    }

    @Test
    fun `expiring base URL is refreshed before download`() = runBlocking {
        db.cloudItemDao().updateBaseUrl(1, "https://lh3/1", now + 60_000)
        downloader.bodies["https://lh3/1-fresh=d"] = photoBytes
        val jobId = enqueueOne()

        assertEquals(TransferState.DONE, engine.run(db.transferJobDao().getById(jobId)!!))
        assertEquals(listOf("s"), refreshed)
        assertEquals(listOf("https://lh3/1-fresh=d"), downloader.requested)
    }

    @Test
    fun `a gone session fails the job permanently and leaves no file`() = runBlocking {
        db.cloudItemDao().updateBaseUrl(1, "https://lh3/1", now)
        refreshFailure = PickerApiException(404, "gone")
        val jobId = enqueueOne()

        assertEquals(TransferState.FAILED, engine.run(db.transferJobDao().getById(jobId)!!))
        val job = db.transferJobDao().getById(jobId)!!
        assertTrue(job.lastError!!.contains("expired"))
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `network failure re-queues until attempts run out and deletes the partial file`() = runBlocking {
        downloader.failWith = TransferException.Network("reset")
        val jobId = enqueueOne()

        repeat(RetryPolicy.MAX_ATTEMPTS - 1) {
            assertEquals(TransferState.QUEUED, engine.run(db.transferJobDao().getById(jobId)!!))
        }
        assertEquals(TransferState.FAILED, engine.run(db.transferJobDao().getById(jobId)!!))

        val job = db.transferJobDao().getById(jobId)!!
        assertEquals(RetryPolicy.MAX_ATTEMPTS, job.attempts)
        assertEquals("reset", job.lastError)
        assertEquals(RetryPolicy.MAX_ATTEMPTS, storage.deleted.size)
        assertTrue(storage.files.isEmpty())
        assertNull(job.sha256)
    }

    @Test
    fun `size mismatch on read-back is a verification failure`() = runBlocking {
        storage.reportedSize = { 999L }
        val jobId = enqueueOne()

        assertEquals(TransferState.QUEUED, engine.run(db.transferJobDao().getById(jobId)!!))
        assertTrue(db.transferJobDao().getById(jobId)!!.lastError!!.startsWith("Size mismatch"))
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `missing destination fails without touching storage`() = runBlocking {
        settings.setDestinationRootUri(null)
        val jobId = enqueueOne()
        assertEquals(TransferState.FAILED, engine.run(db.transferJobDao().getById(jobId)!!))
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `queue operations`() = runBlocking {
        db.cloudItemDao().insert(cloud(2, "VID_2.mp4", created = now).copy(type = "VIDEO", mime = "video/mp4"))
        assertEquals(2, queue.enqueue(listOf(1, 2, 2, 99)))
        assertEquals(0, queue.enqueue(listOf(1, 2)))
        val jobs = db.transferJobDao().getAll()
        assertEquals(listOf(com.gankdat.pomegranate.data.db.entity.Fidelity.GPS_STRIPPED, com.gankdat.pomegranate.data.db.entity.Fidelity.TRANSCODED), jobs.map { it.fidelity })

        queue.pause(jobs[0].id)
        assertEquals(TransferState.PAUSED, db.transferJobDao().getById(jobs[0].id)!!.state)
        queue.resume(jobs[0].id)
        assertEquals(TransferState.QUEUED, db.transferJobDao().getById(jobs[0].id)!!.state)
        queue.pauseAll()
        assertEquals(2, db.transferJobDao().countByState(TransferState.PAUSED))
        queue.resumeAll()
        assertEquals(2, db.transferJobDao().countByState(TransferState.QUEUED))

        queue.cancel(jobs[1].id)
        assertNull(db.transferJobDao().getById(jobs[1].id))

        db.transferJobDao().update(jobs[0].copy(state = TransferState.FAILED, attempts = 5, lastError = "x"))
        assertEquals(1, queue.retryFailed())
        val retried = db.transferJobDao().getById(jobs[0].id)!!
        assertEquals(TransferState.QUEUED, retried.state)
        assertEquals(0, retried.attempts)
        assertNotNull(retried)
    }
}
