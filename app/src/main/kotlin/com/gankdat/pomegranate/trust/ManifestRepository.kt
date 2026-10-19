package com.gankdat.pomegranate.trust

import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.transfer.DestinationLayout
import com.gankdat.pomegranate.transfer.SafStorage
import com.gankdat.pomegranate.transfer.TransferException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal enum class ManifestFormat(val extension: String, val mime: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

internal sealed interface ExportResult {
    /** Path of the written file under the destination root. */
    data class Written(val relativePath: String) : ExportResult

    data object NothingToExport : ExportResult

    data class Failed(val message: String) : ExportResult
}

internal interface ManifestRepository {
    fun observeSummary(): Flow<ManifestSummary>

    fun observeRecords(): Flow<List<ManifestRecord>>

    /** Writes `Pomegranate/manifest-YYYY-MM-DD.<ext>` under the destination root, replacing nothing (a counter is added on collision). */
    suspend fun export(format: ManifestFormat): ExportResult
}

internal class ManifestRepositoryImpl(
    private val jobs: TransferJobDao,
    private val settings: SettingsRepository,
    private val storage: SafStorage,
    private val appVersion: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ManifestRepository {
    override fun observeSummary(): Flow<ManifestSummary> = observeRecords().map(ManifestGenerator::summary)

    override fun observeRecords(): Flow<List<ManifestRecord>> = jobs.observeAll().map(ManifestGenerator::records)

    override suspend fun export(format: ManifestFormat): ExportResult = withContext(ioDispatcher) {
        val records = ManifestGenerator.records(jobs.getAll())
        if (records.isEmpty()) return@withContext ExportResult.NothingToExport
        val root = settings.destinationRootUri.first() ?: return@withContext ExportResult.Failed("No destination folder chosen")
        try {
            val folder = storage.findOrCreateFolder(root, DestinationLayout.ROOT_FOLDER)
            val date = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(clock()).atZone(zone))
            val name = DestinationLayout.uniqueName("manifest-$date.${format.extension}") { storage.childExists(folder, it) }
            val uri = storage.createFile(folder, format.mime, name)
            try {
                storage.openWrite(uri).use { out ->
                    when (format) {
                        ManifestFormat.CSV -> CsvExporter.write(records, out)
                        ManifestFormat.JSON -> JsonExporter.write(records, appVersion, clock(), out)
                    }
                }
            } catch (e: IOException) {
                storage.deleteOwnFile(uri)
                throw e
            }
            ExportResult.Written("${DestinationLayout.ROOT_FOLDER}/$name")
        } catch (e: TransferException) {
            ExportResult.Failed(e.message ?: "Could not write the manifest")
        } catch (e: IOException) {
            ExportResult.Failed(e.message ?: "Could not write the manifest")
        }
    }
}
