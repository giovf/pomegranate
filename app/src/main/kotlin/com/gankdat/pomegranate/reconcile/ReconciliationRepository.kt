package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import com.gankdat.pomegranate.data.model.MediaState
import kotlinx.coroutines.flow.Flow

internal data class ReconciliationResult(
    val cloudCount: Int,
    val deviceCount: Int,
    val exact: Int,
    val likely: Int,
    val ambiguous: Int,
    val groups: Int,
    val durationMillis: Long,
)

/** Per-item states derived from the match table; both maps are complete over their index. */
internal data class StateIndex(
    val cloudStates: Map<Long, MediaState>,
    val deviceStates: Map<Long, MediaState>,
) {
    fun count(state: MediaState): Int =
        cloudStates.values.count { it == state } + if (state == MediaState.DEVICE_ONLY) deviceStates.values.count { it == state } else 0

    companion object {
        val EMPTY = StateIndex(emptyMap(), emptyMap())
    }
}

internal data class AmbiguousMatch(val match: MatchEntity, val evidence: Evidence?)

internal interface ReconciliationRepository {
    /** Full, idempotent reconciliation of the current indexes: rebuilds matches and sibling groups. */
    suspend fun reconcile(): ReconciliationResult

    fun observeStates(): Flow<StateIndex>

    fun observeAmbiguousMatches(): Flow<List<AmbiguousMatch>>

    /**
     * Resolves an ambiguous match with a hash of the cloud file (computed by
     * whoever downloaded it). A match upgrades to EXACT or is removed.
     */
    suspend fun confirmWithCloudHash(cloudItemId: Long, cloudSha256: String): MatchConfidence?

    companion object {
        /** Confidences that count as "the file is on both sides". */
        val CONFIRMED: Set<MatchConfidence> = setOf(MatchConfidence.EXACT, MatchConfidence.LIKELY)
    }
}
