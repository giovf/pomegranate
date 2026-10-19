package com.gankdat.pomegranate.trust

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.settings.SettingsRepositoryImpl
import com.gankdat.pomegranate.transfer.FakeSafStorage
import java.io.File
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ManifestRepositoryImplTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private lateinit var db: PomegranateDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepositoryImpl
    private val storage = FakeSafStorage()
    private val repo by lazy { ManifestRepositoryImpl(db.transferJobDao(), settings, storage, appVersion = "0.1.0", ioDispatcher = Dispatchers.Unconfined, clock = { 1_709_649_000_000 }, zone = ZoneOffset.UTC) }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ContextWrapper(null), PomegranateDatabase::class.java)
            .setDriver(BundledSQLiteDriver()).setJournalMode(RoomDatabase.JournalMode.TRUNCATE).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settings = SettingsRepositoryImpl(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") })
        settings.setDestinationRootUri("tree:root")
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test
    fun `exports land under Pomegranate with dated unique names and summary follows the jobs`() = runBlocking {
        assertEquals(ExportResult.NothingToExport, repo.export(ManifestFormat.CSV))
        db.transferJobDao().insert(
            TransferJobEntity(cloudItemId = 1, sourceId = "g1", originalFilename = "a.jpg", mime = "image/jpeg", state = TransferState.DONE, relativePath = "Pomegranate/2024/2024-03/a.jpg", bytesWritten = 10, sha256 = "00", fidelity = Fidelity.GPS_STRIPPED, completedAt = 5),
        )

        assertEquals(ExportResult.Written("Pomegranate/manifest-2024-03-05.csv"), repo.export(ManifestFormat.CSV))
        assertEquals(ExportResult.Written("Pomegranate/manifest-2024-03-05 (1).csv"), repo.export(ManifestFormat.CSV))
        assertEquals(ExportResult.Written("Pomegranate/manifest-2024-03-05.json"), repo.export(ManifestFormat.JSON))

        val csv = storage.files.values.first().toString("UTF-8")
        assertTrue(csv.startsWith("source_id,"))
        assertTrue(csv.contains("g1,a.jpg,Pomegranate/2024/2024-03/a.jpg,10,00,GPS_STRIPPED,"))
        assertEquals(ManifestSummary(1, 10, 5), repo.observeSummary().first())
    }

    @Test
    fun `missing destination is reported`() = runBlocking {
        settings.setDestinationRootUri(null)
        db.transferJobDao().insert(TransferJobEntity(cloudItemId = 1, sourceId = "g1", originalFilename = "a.jpg", mime = "image/jpeg", state = TransferState.DONE, relativePath = "x/a.jpg", bytesWritten = 1, sha256 = "00", fidelity = Fidelity.GPS_STRIPPED, completedAt = 1))
        assertTrue(repo.export(ManifestFormat.JSON) is ExportResult.Failed)
    }
}
