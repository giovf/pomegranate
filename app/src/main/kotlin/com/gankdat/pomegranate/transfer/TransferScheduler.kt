package com.gankdat.pomegranate.transfer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules [TransferWorker] with constraints derived from settings. */
internal class TransferScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Starts draining now (if constraints allow) and keeps the 15-minute safety net in place. */
    fun start(wifiOnly: Boolean, pauseOnLowBattery: Boolean = true) {
        val constraints = constraints(wifiOnly, pauseOnLowBattery)
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TransferWorker>().setConstraints(constraints).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<TransferWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES).setConstraints(constraints).build(),
        )
    }

    /** Stops the running worker; queued jobs stay queued (callers pause them separately). */
    fun stop() {
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    companion object {
        const val IMMEDIATE_WORK: String = "pomegranate_transfer_now"
        const val PERIODIC_WORK: String = "pomegranate_transfer_periodic"
        const val PERIODIC_MINUTES: Long = 15

        fun constraints(wifiOnly: Boolean, pauseOnLowBattery: Boolean): Constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(pauseOnLowBattery)
            .setRequiresStorageNotLow(true)
            .build()
    }
}
