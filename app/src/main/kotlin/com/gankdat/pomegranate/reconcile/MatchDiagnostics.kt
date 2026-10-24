package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import java.util.Locale
import kotlin.math.abs

/**
 * Explains, in counts only, why items in Google Photos were not found on the
 * phone and where the phone-only items live. Nothing leaves the device; this
 * exists so the owner can see (and report) which matching rule is missing.
 */
internal data class MatchDiagnostics(
    val cloudNotOnPhone: Int,
    /** A phone file has exactly the same name, but its date or size disagrees. */
    val sameNameDifferentDetails: Int,
    /** Same name in another format (HEIC vs JPG and similar). */
    val sameNameOtherFormat: Int,
    /** No phone file with that name, but one with the same pixel size taken within a minute. */
    val renamedCandidates: Int,
    /** Nothing on the phone resembles it: most likely only in Google Photos. */
    val noCandidate: Int,
    /** Pairs the app thinks may be the same but is not sure of; counted as not on the phone. */
    val uncertain: Int,
    /** Phone-only items per folder, largest first (top few). */
    val phoneOnlyFolders: List<Pair<String, Int>>,
    /** Google-only items per file type, largest first. */
    val cloudOnlyTypes: List<Pair<String, Int>>,
) {
    companion object {
        private const val TOP = 6
        private const val MINUTE_MS = 60_000L

        fun analyze(device: List<DeviceItemEntity>, cloud: List<CloudItemEntity>, matches: List<MatchEntity>): MatchDiagnostics {
            val confirmed = matches.filter { it.confidence != MatchConfidence.AMBIGUOUS }
            val matchedCloud = confirmed.map { it.cloudItemId }.toHashSet()
            val matchedDevice = confirmed.map { it.deviceItemId }.toHashSet()
            val unmatchedCloud = cloud.filter { it.id !in matchedCloud }
            val byName = device.groupBy { it.displayName.lowercase(Locale.ROOT) }
            val byBase = device.groupBy { it.displayName.substringBeforeLast('.').lowercase(Locale.ROOT) }
            val byDims = device.filter { it.width > 0 }.groupBy { it.width to it.height }

            var sameName = 0
            var otherFormat = 0
            var renamed = 0
            var none = 0
            for (c in unmatchedCloud) {
                val name = c.filename.lowercase(Locale.ROOT)
                when {
                    byName.containsKey(name) -> sameName++
                    byBase.containsKey(name.substringBeforeLast('.')) -> otherFormat++
                    byDims[c.width to c.height].orEmpty().any { c.createTime > 0 && abs(it.dateTaken - c.createTime) <= MINUTE_MS } -> renamed++
                    else -> none++
                }
            }
            val phoneOnly = device.filter { it.id !in matchedDevice }
            return MatchDiagnostics(
                cloudNotOnPhone = unmatchedCloud.size,
                sameNameDifferentDetails = sameName,
                sameNameOtherFormat = otherFormat,
                renamedCandidates = renamed,
                noCandidate = none,
                uncertain = matches.count { it.confidence == MatchConfidence.AMBIGUOUS },
                phoneOnlyFolders = phoneOnly.groupingBy { it.bucket.ifBlank { "?" } }.eachCount().entries.sortedByDescending { it.value }.take(TOP).map { it.key to it.value },
                cloudOnlyTypes = unmatchedCloud.groupingBy { it.filename.substringAfterLast('.', "?").uppercase(Locale.ROOT) }.eachCount().entries.sortedByDescending { it.value }.take(TOP).map { it.key to it.value },
            )
        }
    }
}
