package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.SessionRange
import kotlinx.coroutines.flow.Flow

internal sealed interface SessionState {
    data object Selecting : SessionState

    data object Complete : SessionState

    data object Timeout : SessionState

    data class Error(val message: String) : SessionState
}

internal data class CloudCoverage(val itemCount: Int, val roundCount: Int)

internal interface CloudScannerRepository {
    /** Creates a Picker session at Google and records the round locally. Throws on failure. */
    suspend fun startPickerRound(): PickerSession

    /** Blocks (with backoff) until the user finishes picking, the session times out, or an error occurs. */
    suspend fun pollSession(sessionId: String): SessionState

    /** Lists every picked item into `cloud_item`, marks the round indexed and returns the item count. */
    suspend fun indexSession(sessionId: String): Int

    /** Re-lists the session if any of its base URLs expire within [marginMillis]; marks the round expired if Google no longer has it. */
    suspend fun refreshExpiringBaseUrls(sessionId: String, marginMillis: Long = DEFAULT_REFRESH_MARGIN_MILLIS)

    /** Re-lists the session unconditionally (a link was rejected before its nominal expiry). */
    suspend fun refreshBaseUrls(sessionId: String)

    /**
     * Asks Google whether it still serves this round. True when it does not
     * (the session is gone or past its expiry time, after which Google ends
     * access to its items); the round is then marked expired locally. False
     * when it does; null when that could not be found out (offline, signed out).
     */
    suspend fun isRoundClosed(sessionId: String): Boolean?

    /** Whether the round is already recorded as closed; no network. */
    suspend fun isRoundKnownClosed(sessionId: String): Boolean

    /**
     * Asks Google whether each indexed round can still be listed, and marks
     * the ones it has closed as expired. Network and sign-in problems leave
     * everything as it was. Returns how many rounds were found closed.
     */
    suspend fun checkIndexedRounds(): Int

    /** Tells Google to discard the session; local items stay. */
    suspend fun deleteSession(sessionId: String)

    /**
     * The round still waiting for Done, if any, as Google currently sees it.
     * Rounds Google has dropped, and older open rounds, are marked expired.
     */
    suspend fun openRound(): PickerSession?

    fun sessionRanges(): Flow<List<SessionRange>>

    fun coverage(): Flow<CloudCoverage>

    fun pickerRounds(): Flow<List<PickerRoundEntity>>

    companion object {
        const val DEFAULT_REFRESH_MARGIN_MILLIS: Long = 10 * 60_000
    }
}
