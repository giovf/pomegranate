package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.PickerApiException
import com.gankdat.pomegranate.census.cloud.PickerAuthException
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.trust.Sha256
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Runs one transfer job end to end: fresh base URL, destination folder,
 * streamed download straight into the SAF document, then size and SHA-256
 * verification before the job is reported as DONE. A write that fails
 * verification is deleted (it is this app's own unverified file) and the
 * job is retried or failed per [RetryPolicy]. Pure apart from the injected
 * storage, downloader and registrar, so the whole path is unit-tested.
 */
internal class TransferEngine(
    private val jobs: TransferJobDao,
    private val cloudItems: CloudItemDao,
    private val settings: SettingsRepository,
    private val cloudScanner: CloudScannerRepository,
    private val reconciliation: ReconciliationRepository,
    private val storage: SafStorage,
    private val downloader: Downloader,
    private val registrar: MediaStoreRegistrar,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val folderCache = ConcurrentHashMap<String, String>()
    private val _liveProgress = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Bytes written so far per running job id; cleared when the job finishes either way. */
    val liveProgress: StateFlow<Map<Long, Long>> = _liveProgress.asStateFlow()

    /** Processes [job]; returns its final state for this attempt. */
    suspend fun run(job: TransferJobEntity): TransferState = withContext(ioDispatcher) {
        val root = settings.destinationRootUri.first() ?: return@withContext fail(job, TransferException.Storage("No destination folder chosen"))
        val item = cloudItems.getById(job.cloudItemId) ?: run {
            jobs.update(job.copy(state = TransferState.FAILED, lastError = "Cloud item no longer indexed"))
            return@withContext TransferState.FAILED
        }
        val attempt = job.copy(state = TransferState.RUNNING, attempts = job.attempts + 1, lastError = null, bytesWritten = 0)
        jobs.update(attempt)
        try {
            val fresh = refreshedItem(item)
            val (folderUri, folderPath) = destinationFolder(root, fresh)
            val isVideo = fresh.type == "VIDEO" || fresh.mime.startsWith("video/")
            val name = DestinationLayout.uniqueName(fresh.filename.ifBlank { fresh.pickerMediaItemId }) { storage.childExists(folderUri, it) }
            val fileUri = storage.createFile(folderUri, fresh.mime, name)
            val relativePath = "$folderPath/$name"
            var written = 0L
            try {
                storage.openWrite(fileUri).use { sink ->
                    written = downloader.download(fresh.baseUrl + FidelityClassifier.downloadSuffix(isVideo), sink) { bytes ->
                        _liveProgress.update { it + (attempt.id to bytes) }
                    }
                }
                currentCoroutineContext().ensureActive()
                jobs.update(attempt.copy(state = TransferState.VERIFYING, destinationUri = fileUri, relativePath = relativePath, bytesWritten = written, bytesExpected = written))
                val sha256 = verify(fileUri, written)
                registrar.register(storage.documentId(fileUri), fresh.mime)
                jobs.update(
                    attempt.copy(
                        state = TransferState.DONE,
                        destinationUri = fileUri,
                        relativePath = relativePath,
                        bytesWritten = written,
                        bytesExpected = written,
                        sha256 = sha256,
                        completedAt = clock(),
                    ),
                )
                reconciliation.confirmWithCloudHash(fresh.id, sha256)
                TransferState.DONE
            } catch (e: TransferException) {
                storage.deleteOwnFile(fileUri)
                throw e
            } catch (e: IOException) {
                storage.deleteOwnFile(fileUri)
                throw TransferException.Storage(e.message ?: "write failed")
            }
        } catch (e: TransferException) {
            fail(attempt, e)
        } finally {
            _liveProgress.update { it - attempt.id }
        }
    }

    private suspend fun refreshedItem(item: CloudItemEntity): CloudItemEntity {
        if (item.baseUrlExpiresAt - clock() > REFRESH_MARGIN_MILLIS) return item
        try {
            cloudScanner.refreshExpiringBaseUrls(item.sessionId, marginMillis = REFRESH_MARGIN_MILLIS)
        } catch (e: PickerApiException) {
            throw if (e.isGone) TransferException.SessionGone() else if (e.isAuthFailure) TransferException.NotSignedIn() else TransferException.Http(e.httpStatus)
        } catch (_: PickerAuthException) {
            throw TransferException.NotSignedIn()
        } catch (e: IOException) {
            throw TransferException.Network(e.message ?: "could not refresh download link")
        }
        return cloudItems.getById(item.id) ?: item
    }

    private suspend fun destinationFolder(root: String, item: CloudItemEntity): Pair<String, String> {
        val strategy = settings.folderStrategy.first()
        val folders = DestinationLayout.folders(item.createTime, item.userOverrideOrigin ?: item.inferredOrigin, strategy)
        var parent = root
        var path = ""
        for (name in folders) {
            path = if (path.isEmpty()) name else "$path/$name"
            parent = folderCache.getOrPut("$root|$path") {
                try {
                    storage.findOrCreateFolder(parent, name)
                } catch (e: IOException) {
                    throw TransferException.Storage(e.message ?: "could not create $name")
                }
            }
        }
        return parent to path
    }

    /** Re-reads the written document: the provider's size must match and the content hash is recorded. */
    private fun verify(fileUri: String, expectedBytes: Long): String {
        val reported = storage.size(fileUri)
        if (reported != null && reported != expectedBytes) throw TransferException.Verification("Size mismatch: wrote $expectedBytes, provider reports $reported")
        var counted = 0L
        val sha = try {
            storage.openRead(fileUri).use { Sha256.hex(it) { counted = it } }
        } catch (e: IOException) {
            throw TransferException.Verification(e.message ?: "could not read back file")
        }
        if (counted != expectedBytes) throw TransferException.Verification("Size mismatch: wrote $expectedBytes, read back $counted")
        if (expectedBytes == 0L) throw TransferException.Verification("Empty download")
        return sha
    }

    private suspend fun fail(job: TransferJobEntity, error: TransferException): TransferState {
        val next = RetryPolicy.nextState(error, job.attempts)
        jobs.update(job.copy(state = next, lastError = RetryPolicy.describe(error), destinationUri = "", relativePath = "", bytesWritten = 0, sha256 = null))
        return next
    }

    companion object {
        /** Refresh links that expire within five minutes; a large video can take that long. */
        const val REFRESH_MARGIN_MILLIS: Long = 5 * 60_000
    }
}
