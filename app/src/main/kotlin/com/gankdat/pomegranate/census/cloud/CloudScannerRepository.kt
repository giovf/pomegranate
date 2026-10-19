package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
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

    /** Tells Google to discard the session; local items stay. */
    suspend fun deleteSession(sessionId: String)

    fun coverage(): Flow<CloudCoverage>

    fun pickerRounds(): Flow<List<PickerRoundEntity>>

    companion object {
        const val DEFAULT_REFRESH_MARGIN_MILLIS: Long = 10 * 60_000
    }
}
