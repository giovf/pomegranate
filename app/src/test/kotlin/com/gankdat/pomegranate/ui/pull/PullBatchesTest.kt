package com.gankdat.pomegranate.ui.pull

import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PullBatchesTest {
    private fun job(id: Long, batch: Long, state: TransferState, bytes: Long = 100, completedAt: Long? = null) = TransferJobEntity(
        id = id, cloudItemId = id, sourceId = "g$id", originalFilename = "f$id.jpg", mime = "image/jpeg", state = state,
        bytesWritten = bytes, fidelity = Fidelity.GPS_STRIPPED, completedAt = completedAt, batchId = batch,
    )

    private val jobs = listOf(
        job(1, batch = 0, state = TransferState.DONE, completedAt = 500),
        job(2, batch = 1_000, state = TransferState.DONE, completedAt = 1_500),
        job(3, batch = 1_000, state = TransferState.FAILED, bytes = 0),
        job(4, batch = 2_000, state = TransferState.DONE, completedAt = 2_500),
        job(5, batch = 3_000, state = TransferState.DONE, completedAt = 3_100),
        job(6, batch = 3_000, state = TransferState.QUEUED, bytes = 0),
    )

    @Test
    fun `the pull with work left is the active one and is not history yet`() {
        assertEquals(3_000L, PullBatches.activeBatch(jobs))
        assertNull(PullBatches.activeBatch(jobs.filter { it.batchId != 3_000L }))
        assertEquals(listOf(2_000L, 1_000L, 0L), PullBatches.history(jobs, cleanupConfirmedAt = 0).map { it.batchId })
    }

    @Test
    fun `each finished pull carries its own counts and status`() {
        val history = PullBatches.history(jobs, cleanupConfirmedAt = 2_000).associateBy { it.batchId }

        assertEquals(PullBatch(1_000, done = 1, failed = 1, bytesWritten = 100, completedAt = 1_500, cleanedUp = false), history[1_000L])
        assertEquals(true, history.getValue(0L).cleanedUp) // verified before the clean-up was confirmed
        assertEquals(false, history.getValue(2_000L).cleanedUp) // pulled after it
    }
}
