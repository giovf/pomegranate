package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.PickerApiException
import com.gankdat.pomegranate.census.cloud.PickerAuthException
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.trust.Sha256
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
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
    /** The user's current Google access token, refreshed as needed; null when signed out. */
    private val accessToken: suspend () -> String?,
    /** App-private folder for a photo and its clip while a motion photo is checked and rebuilt. */
    private val scratchDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val folderCache = ConcurrentHashMap<String, String>()
    private val _liveProgress = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Bytes written so far per running job id; cleared when the job finishes either way. */
    val liveProgress: StateFlow<Map<Long, Long>> = _liveProgress.asStateFlow()

    /** Jobs this process is working on right now; anything else found RUNNING or VERIFYING was interrupted. */
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Puts jobs back in the queue that were left RUNNING or VERIFYING by a
     * stopped worker or a crash, removing the unverified file each had
     * started (this app's own, never reported as saved). Returns how many.
     */
    suspend fun recoverInterrupted(): Int = withContext(ioDispatcher) {
        val orphans = (jobs.getByState(TransferState.RUNNING) + jobs.getByState(TransferState.VERIFYING)).filter { it.id !in inFlight }
        for (job in orphans) {
            if (job.destinationUri.isNotEmpty()) {
                try {
                    storage.deleteOwnFile(job.destinationUri)
                } catch (_: RuntimeException) {
                    // The folder may be gone or no longer granted; the job is retried either way.
                }
            }
            jobs.update(job.copy(state = TransferState.QUEUED, destinationUri = "", relativePath = "", bytesWritten = 0, sha256 = null))
        }
        orphans.size
    }

    /** Processes [job]; returns its final state for this attempt. */
    suspend fun run(job: TransferJobEntity): TransferState {
        inFlight += job.id
        try {
            return runAttempt(job)
        } finally {
            inFlight -= job.id
        }
    }

    private suspend fun runAttempt(job: TransferJobEntity): TransferState = withContext(ioDispatcher) {
        val root = settings.destinationRootUri.first() ?: return@withContext fail(job, TransferException.Storage("No destination folder chosen"))
        val item = cloudItems.getById(job.cloudItemId) ?: run {
            jobs.update(job.copy(state = TransferState.FAILED, lastError = "Cloud item no longer indexed"))
            return@withContext TransferState.FAILED
        }
        var attempt = job.copy(state = TransferState.RUNNING, attempts = job.attempts + 1, lastError = null, bytesWritten = 0)
        jobs.update(attempt)
        try {
            val fresh = refreshedItem(item)
            val token = accessToken() ?: throw TransferException.NotSignedIn()
            val (folderUri, folderPath) = destinationFolder(root, fresh)
            val isVideo = fresh.type == "VIDEO" || fresh.mime.startsWith("video/")
            val name = DestinationLayout.uniqueName(fresh.filename.ifBlank { fresh.pickerMediaItemId }) { storage.childExists(folderUri, it) }
            val fileUri = storage.createFile(folderUri, fresh.mime, name)
            val relativePath = "$folderPath/$name"
            // Recorded at once, so an interrupted attempt can find and remove its partial file.
            attempt = attempt.copy(destinationUri = fileUri, relativePath = relativePath)
            jobs.update(attempt)
            var written = 0L
            var fidelity = attempt.fidelity
            try {
                if (isVideo) {
                    storage.openWrite(fileUri).use { sink ->
                        written = downloader.download(fresh.baseUrl + FidelityClassifier.downloadSuffix(isVideo = true), token, sink) { bytes ->
                            _liveProgress.update { it + (attempt.id to bytes) }
                        }
                    }
                } else {
                    val photo = pullPhoto(attempt.id, fresh, token, fileUri)
                    written = photo.first
                    fidelity = photo.second
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
                        fidelity = fidelity,
                        completedAt = clock(),
                    ),
                )
                // A rebuilt motion photo is our own assembly, so its hash says nothing about the phone's copy.
                if (fidelity != Fidelity.MOTION_REBUILT) reconciliation.confirmWithCloudHash(fresh.id, sha256)
                TransferState.DONE
            } catch (e: TransferException) {
                storage.deleteOwnFile(fileUri)
                throw e
            } catch (e: IOException) {
                storage.deleteOwnFile(fileUri)
                throw TransferException.Storage(e.message ?: "write failed")
            } catch (e: CancellationException) {
                throw e // paused or stopped: recoverInterrupted() cleans up before the next run
            } catch (e: RuntimeException) {
                // A storage provider misbehaving must fail this one job with a reason, not leave it "verifying" for ever.
                storage.deleteOwnFile(fileUri)
                throw TransferException.Storage("unexpected ${e::class.simpleName}")
            }
        } catch (e: TransferException) {
            // Find out (and record) whether the whole round is closed, so its other items fail at once instead of one request each.
            if (e is TransferException.SessionGone) cloudScanner.isRoundClosed(item.sessionId)
            fail(attempt, e)
        } finally {
            _liveProgress.update { it - attempt.id }
        }
    }

    /**
     * Photos land in scratch first so a motion photo can be recognised. Its
     * clip is fetched only when the still or its name says it is one; a clip
     * Google will not serve (4xx, or not a video) leaves the still, labelled
     * as such. Network and server errors still fail the attempt so it retries.
     * Returns bytes written to [fileUri] and the fidelity of what was written.
     */
    private fun pullPhoto(jobId: Long, item: CloudItemEntity, token: String, fileUri: String): Pair<Long, Fidelity> {
        scratchDir.mkdirs()
        val stillFile = File.createTempFile("still", ".part", scratchDir)
        val clipFile = File.createTempFile("clip", ".part", scratchDir)
        try {
            val stillBytes = stillFile.outputStream().buffered().use { sink ->
                downloader.download(item.baseUrl + FidelityClassifier.downloadSuffix(isVideo = false), token, sink) { bytes ->
                    _liveProgress.update { it + (jobId to bytes) }
                }
            }
            val still = stillFile.readBytes()
            if (still.size.toLong() != stillBytes) throw TransferException.Verification("Size mismatch in scratch copy")
            val plan = MotionPhotos.plan(item.filename, still)
            val clipLength = if (plan == MotionPlan.FETCH_CLIP) fetchClip(jobId, item, token, stillBytes, clipFile) else null
            return storage.openWrite(fileUri).use { sink ->
                when {
                    clipLength != null -> clipFile.inputStream().buffered().use { clip ->
                        val head = clipFile.inputStream().use { it.readNBytes(12) }
                        MotionPhotoWriter.write(still, clip, clipLength, MotionPhotos.clipMime(head), sink) to Fidelity.MOTION_REBUILT
                    }
                    else -> {
                        sink.write(still)
                        sink.flush()
                        val fidelity = if (plan == MotionPlan.FETCH_CLIP || plan == MotionPlan.STILL_ONLY) Fidelity.MOTION_STILL_ONLY else Fidelity.GPS_STRIPPED
                        still.size.toLong() to fidelity
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw TransferException.Verification(e.message ?: "could not rebuild motion photo")
        } finally {
            stillFile.delete()
            clipFile.delete()
        }
    }

    /** Downloads the clip half into [clipFile]; null when Google has no usable clip for this photo. */
    private fun fetchClip(jobId: Long, item: CloudItemEntity, token: String, stillBytes: Long, clipFile: File): Long? {
        val length = try {
            clipFile.outputStream().buffered().use { sink ->
                downloader.download(item.baseUrl + FidelityClassifier.downloadSuffix(isVideo = true), token, sink) { bytes ->
                    _liveProgress.update { it + (jobId to stillBytes + bytes) }
                }
            }
        } catch (e: TransferException.SessionGone) {
            return null // 400/403/404: no video half for this item
        } catch (e: TransferException.Http) {
            if (e.retryable) throw e
            return null
        }
        val head = clipFile.inputStream().use { it.readNBytes(12) }
        return length.takeIf { it > 0 && MotionPhotos.isMp4(head) }
    }

    private suspend fun refreshedItem(item: CloudItemEntity): CloudItemEntity {
        if (cloudScanner.isRoundKnownClosed(item.sessionId)) throw TransferException.SessionGone()
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
