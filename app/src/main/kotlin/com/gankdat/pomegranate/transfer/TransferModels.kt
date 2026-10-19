package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferState

/** Aggregate view of the queue for progress UI. */
internal data class TransferSummary(
    val queued: Int = 0,
    val running: Int = 0,
    val verifying: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val paused: Int = 0,
    val bytesWritten: Long = 0,
    val bytesExpected: Long = 0,
) {
    val total: Int get() = queued + running + verifying + done + failed + paused
    val isActive: Boolean get() = queued + running + verifying > 0

    companion object {
        fun of(states: List<Pair<TransferState, Pair<Long, Long>>>): TransferSummary {
            var s = TransferSummary()
            for ((state, bytes) in states) {
                s = when (state) {
                    TransferState.QUEUED -> s.copy(queued = s.queued + 1)
                    TransferState.RUNNING -> s.copy(running = s.running + 1)
                    TransferState.VERIFYING -> s.copy(verifying = s.verifying + 1)
                    TransferState.DONE -> s.copy(done = s.done + 1)
                    TransferState.FAILED -> s.copy(failed = s.failed + 1)
                    TransferState.PAUSED -> s.copy(paused = s.paused + 1)
                }
                s = s.copy(bytesWritten = s.bytesWritten + bytes.first, bytesExpected = s.bytesExpected + bytes.second)
            }
            return s
        }
    }
}

/**
 * What the Picker API hands out, per Google's documentation: photos keep
 * EXIF minus GPS (`=d`), videos are transcoded (`=dv`). Nothing from the
 * Picker is an untouched original; a future Takeout source can return
 * [Fidelity.ORIGINAL].
 */
internal object FidelityClassifier {
    fun forPicker(isVideo: Boolean): Fidelity = if (isVideo) Fidelity.TRANSCODED else Fidelity.GPS_STRIPPED

    /** Download URL suffix: full-resolution photo bytes or the (transcoded) video. */
    fun downloadSuffix(isVideo: Boolean): String = if (isVideo) "=dv" else "=d"
}

/** Why a transfer attempt stopped. */
internal sealed class TransferException(message: String, val retryable: Boolean) : Exception(message) {
    class Network(message: String) : TransferException(message, retryable = true)

    class Http(val status: Int) : TransferException("HTTP $status", retryable = status == 429 || status >= 500)

    class SessionGone : TransferException("Picker session expired; index the item again", retryable = false)

    class Storage(message: String) : TransferException(message, retryable = false)

    class Verification(message: String) : TransferException(message, retryable = true)

    class NotSignedIn : TransferException("Not signed in to Google", retryable = false)
}
