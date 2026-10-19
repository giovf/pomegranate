package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface TransferQueue {
    /** Queues jobs for the given cloud items, skipping any already queued, running or done. Returns how many were added. */
    suspend fun enqueue(cloudItemIds: List<Long>): Int

    suspend fun pause(jobId: Long)

    suspend fun resume(jobId: Long)

    /** Removes a job that has not completed; a DONE job is proof of copy and stays. */
    suspend fun cancel(jobId: Long)

    suspend fun pauseAll()

    suspend fun resumeAll()

    /** Drops every job that is not DONE. */
    suspend fun cancelAll(): Int

    /** Re-queues every FAILED job with its attempt counter reset. */
    suspend fun retryFailed(): Int

    fun observeJobs(): Flow<List<TransferJobEntity>>

    fun observeSummary(): Flow<TransferSummary>
}

internal class TransferQueueImpl(
    private val jobs: TransferJobDao,
    private val cloudItems: CloudItemDao,
) : TransferQueue {
    override suspend fun enqueue(cloudItemIds: List<Long>): Int {
        val existing = jobs.getActiveOrDoneCloudItemIds().toHashSet()
        val fresh = cloudItemIds.filter { it !in existing }.distinct()
        if (fresh.isEmpty()) return 0
        val items = cloudItems.getAll().associateBy { it.id }
        val entities = fresh.mapNotNull { id ->
            val item = items[id] ?: return@mapNotNull null
            TransferJobEntity(
                cloudItemId = id,
                sourceId = item.pickerMediaItemId,
                originalFilename = item.filename,
                mime = item.mime,
                fidelity = FidelityClassifier.forPicker(isVideo = item.type == "VIDEO" || item.mime.startsWith("video/")),
            )
        }
        jobs.insertAll(entities)
        return entities.size
    }

    override suspend fun pause(jobId: Long) {
        val job = jobs.getById(jobId) ?: return
        if (job.state == TransferState.QUEUED || job.state == TransferState.RUNNING) jobs.setState(jobId, TransferState.PAUSED)
    }

    override suspend fun resume(jobId: Long) {
        val job = jobs.getById(jobId) ?: return
        if (job.state == TransferState.PAUSED) jobs.setState(jobId, TransferState.QUEUED)
    }

    override suspend fun cancel(jobId: Long) {
        val job = jobs.getById(jobId) ?: return
        if (job.state != TransferState.DONE) jobs.delete(job)
    }

    override suspend fun pauseAll() {
        jobs.setStateForAll(TransferState.QUEUED, TransferState.PAUSED)
        jobs.setStateForAll(TransferState.RUNNING, TransferState.PAUSED)
    }

    override suspend fun resumeAll() {
        jobs.setStateForAll(TransferState.PAUSED, TransferState.QUEUED)
    }

    override suspend fun cancelAll(): Int = jobs.deleteNotDone()

    override suspend fun retryFailed(): Int {
        val failed = jobs.getByState(TransferState.FAILED)
        failed.forEach { jobs.update(it.copy(state = TransferState.QUEUED, attempts = 0, lastError = null, bytesWritten = 0)) }
        return failed.size
    }

    override fun observeJobs(): Flow<List<TransferJobEntity>> = jobs.observeAll()

    override fun observeSummary(): Flow<TransferSummary> =
        jobs.observeAll().map { all -> TransferSummary.of(all.map { it.state to (it.bytesWritten to it.bytesExpected) }) }
}
