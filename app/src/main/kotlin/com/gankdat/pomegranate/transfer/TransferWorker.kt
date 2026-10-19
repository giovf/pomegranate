package com.gankdat.pomegranate.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gankdat.pomegranate.PomegranateApplication
import com.gankdat.pomegranate.data.db.entity.TransferState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Drains the queue with bounded concurrency until nothing is QUEUED, as a
 * foreground job with a progress notification. Jobs that fail retryably go
 * back to QUEUED and are picked up by the next run, which the periodic
 * schedule guarantees within 15 minutes.
 */
internal class TransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val app = PomegranateApplication.from(applicationContext)
        return TransferNotifications.foregroundInfo(applicationContext, app.transferQueue.observeSummary().first())
    }

    override suspend fun doWork(): Result {
        val app = PomegranateApplication.from(applicationContext)
        val engine = app.transferEngine
        val jobs = app.database.transferJobDao()
        val queue = app.transferQueue
        val concurrency = app.settings.downloadConcurrency.first()
        val semaphore = Semaphore(concurrency)

        val foreground = try {
            setForeground(getForegroundInfo())
            true
        } catch (_: IllegalStateException) {
            false // Foreground start refused (background restrictions); keep going without the notification.
        }

        coroutineScope {
            val progressUpdater = if (foreground) {
                launch {
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MILLIS)
                        setForeground(TransferNotifications.foregroundInfo(applicationContext, queue.observeSummary().first()))
                    }
                }
            } else {
                null
            }
            var passes = 0
            while (!isStopped && passes++ < MAX_PASSES) {
                val batch = jobs.getNextByState(TransferState.QUEUED, limit = concurrency * 2)
                if (batch.isEmpty()) break
                coroutineScope {
                    for (job in batch) {
                        launch { semaphore.withPermit { if (!isStopped) engine.run(job) } }
                    }
                }
            }
            progressUpdater?.cancel()
        }
        return Result.success()
    }

    private companion object {
        /** Guard against a job that keeps re-queueing itself within one run. */
        const val MAX_PASSES = 1_000
        const val PROGRESS_INTERVAL_MILLIS = 1_500L
    }
}
