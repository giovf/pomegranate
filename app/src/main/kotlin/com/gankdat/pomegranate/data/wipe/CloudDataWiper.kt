package com.gankdat.pomegranate.data.wipe

import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.db.dao.PickerRoundDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.TransferState

internal data class WipeCounts(val cloudItems: Int, val matches: Int, val rounds: Int, val droppedJobs: Int)

/**
 * Forgets everything learned from Google: the cloud index, the matches
 * against it and the picker rounds. Kept on purpose: the device census,
 * DONE transfer jobs (proof of copy) and origin rules.
 */
internal class CloudDataWiper(
    private val cloudItems: CloudItemDao,
    private val matches: MatchDao,
    private val rounds: PickerRoundDao,
    private val jobs: TransferJobDao,
) {
    suspend fun wipe(): WipeCounts {
        val counts = WipeCounts(
            cloudItems = cloudItems.count(),
            matches = matches.count(),
            rounds = rounds.getAll().size,
            droppedJobs = jobs.getAll().count { it.state != TransferState.DONE },
        )
        jobs.deleteNotDone() // Unfinished jobs point at items that are about to vanish.
        rounds.deleteAll() // Cascades to cloud_item and, from there, to match and group membership.
        cloudItems.deleteAll() // Belt and braces for items whose round was already gone.
        matches.deleteAll()
        return counts
    }
}
