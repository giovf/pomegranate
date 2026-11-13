package com.gankdat.pomegranate.ui.pull

import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState

/** One finished pull, as shown on its card. */
internal data class PullBatch(
    /** When the pull was started (epoch millis); 0 groups pulls made before pulls were told apart. */
    val batchId: Long,
    val done: Int,
    val failed: Int,
    val bytesWritten: Long,
    /** When its last file was verified; null if nothing was. */
    val completedAt: Long?,
    /** Everything in it was verified before the user confirmed their Google Photos clean-up. */
    val cleanedUp: Boolean,
)

/** Groups transfer jobs into the pulls they were started in. Pure. */
internal object PullBatches {
    private val OPEN = setOf(TransferState.QUEUED, TransferState.RUNNING, TransferState.VERIFYING, TransferState.PAUSED)

    /** The pull that still has work to do, if any (the newest when failed items of several were retried). */
    fun activeBatch(jobs: List<TransferJobEntity>): Long? = jobs.filter { it.state in OPEN }.maxOfOrNull { it.batchId }

    /** Pulls with nothing left to do, newest first. */
    fun history(jobs: List<TransferJobEntity>, cleanupConfirmedAt: Long): List<PullBatch> =
        jobs.groupBy { it.batchId }
            .filterValues { members -> members.none { it.state in OPEN } }
            .map { (id, members) ->
                val done = members.filter { it.state == TransferState.DONE }
                val failed = members.count { it.state == TransferState.FAILED }
                val completedAt = done.mapNotNull { it.completedAt }.maxOrNull()
                PullBatch(
                    batchId = id,
                    done = done.size,
                    failed = failed,
                    bytesWritten = done.sumOf { it.bytesWritten },
                    completedAt = completedAt,
                    cleanedUp = failed == 0 && completedAt != null && cleanupConfirmedAt > 0 && completedAt <= cleanupConfirmedAt,
                )
            }
            .sortedByDescending { it.batchId }
}
