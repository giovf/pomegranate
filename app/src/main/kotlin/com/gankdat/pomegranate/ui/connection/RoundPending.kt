package com.gankdat.pomegranate.ui.connection

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * What one round still owes: its items that are only in Google Photos and
 * have no verified download yet. For a round Google has closed this is
 * exactly what has to be picked again, so it carries the dates to look for.
 */
internal data class RoundPending(
    val cloudItemIds: Set<Long>,
    val oldest: Long?,
    val newest: Long?,
    /** Items per month, newest month first (the order Google's picker lists them); undated items are not included. */
    val months: List<Pair<YearMonth, Int>>,
) {
    val count: Int get() = cloudItemIds.size

    companion object {
        /**
         * [onPhone] holds the row ids of cloud items matched to a phone file;
         * [pulled] the Google media ids with a verified download.
         */
        fun bySession(items: List<CloudItemEntity>, onPhone: Set<Long>, pulled: Set<String>, zone: ZoneId = ZoneId.systemDefault()): Map<String, RoundPending> =
            items.filter { it.id !in onPhone && it.pickerMediaItemId !in pulled }
                .groupBy { it.sessionId }
                .mapValues { (_, members) ->
                    val dated = members.filter { it.createTime > 0 }
                    RoundPending(
                        cloudItemIds = members.map { it.id }.toSet(),
                        oldest = dated.minOfOrNull { it.createTime },
                        newest = dated.maxOfOrNull { it.createTime },
                        months = dated.groupingBy { YearMonth.from(Instant.ofEpochMilli(it.createTime).atZone(zone)) }
                            .eachCount().entries.sortedByDescending { it.key }.map { it.key to it.value },
                    )
                }
    }
}
