package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.data.db.entity.TransferState

/** Decides what a failed attempt becomes; pure. */
internal object RetryPolicy {
    const val MAX_ATTEMPTS: Int = 5

    fun nextState(error: TransferException, attemptsSoFar: Int): TransferState =
        if (error.retryable && attemptsSoFar < MAX_ATTEMPTS) TransferState.QUEUED else TransferState.FAILED

    /** Non-identifying message for `transfer_job.lastError`. */
    fun describe(error: TransferException): String = error.message ?: error::class.simpleName.orEmpty()
}
