package com.gankdat.pomegranate.census.cloud

/**
 * Works out, per indexed round, how many of its items were already indexed
 * from an earlier round. Uses the recorded counts where they exist. For
 * rounds indexed before counts were recorded: the oldest round cannot
 * overlap anything, and if exactly one round is still unknown its overlap
 * follows from the totals. Otherwise the round stays unknown (null).
 */
internal object RoundAccounting {
    data class Round(val sessionId: String, val selected: Int)

    /** [roundsOldestFirst] are the indexed rounds; [totalIndexed] is the number of distinct indexed items. */
    fun alreadyIndexed(roundsOldestFirst: List<Round>, recorded: Map<String, Int>, totalIndexed: Int): Map<String, Int?> {
        val result = LinkedHashMap<String, Int?>()
        roundsOldestFirst.forEachIndexed { i, round -> result[round.sessionId] = recorded[round.sessionId] ?: if (i == 0) 0 else null }
        val unknown = result.filterValues { it == null }.keys
        if (unknown.size == 1) {
            val id = unknown.single()
            val newElsewhere = roundsOldestFirst.filter { it.sessionId != id }.sumOf { it.selected - (result[it.sessionId] ?: 0) }
            val selected = roundsOldestFirst.first { it.sessionId == id }.selected
            val overlap = selected - (totalIndexed - newElsewhere)
            if (overlap in 0..selected) result[id] = overlap
        }
        return result
    }
}
