package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import java.util.Locale
import kotlin.math.abs

/** A proposed pairing with the confidence the engine assigns and the evidence behind it. */
internal data class MatchProposal(
    val cloudItemId: Long,
    val deviceItemId: Long,
    val confidence: MatchConfidence,
    val evidence: Evidence,
)

/**
 * Deterministic matching between the cloud index and the device census.
 * Pure: takes the two lists and returns proposals, so every rule is unit
 * testable. Google never reports file size, so identity rests on filename,
 * capture timestamp and pixel dimensions, with SHA-256 reserved for the
 * ambiguous leftovers.
 */
internal object MatchingEngine {
    const val TIMESTAMP_TOLERANCE_MS: Long = 1_000
    private const val HALF_HOUR_MS = 30 * 60_000L
    private const val MAX_ZONE_SHIFT_MS = 14 * 60 * 60_000L
    private const val ONE_DAY_MS = 24 * 60 * 60_000L

    fun match(cloudItems: List<CloudItemEntity>, deviceItems: List<DeviceItemEntity>): List<MatchProposal> {
        val byName = deviceItems.groupBy { it.displayName.lowercase(Locale.ROOT) }
        val byDimensions = deviceItems.filter { it.width > 0 && it.height > 0 }.groupBy { it.width to it.height }
        val claimed = HashSet<Long>()
        val proposals = ArrayList<MatchProposal>()

        // Primary pass first for every cloud item so exact pairs claim their device rows before looser rules run.
        val unmatched = ArrayList<CloudItemEntity>()
        for (cloud in cloudItems) {
            val proposal = primary(cloud, byName[cloud.filename.lowercase(Locale.ROOT)].orEmpty().filter { it.id !in claimed })
            if (proposal != null) {
                proposals += proposal
                claimed += proposal.deviceItemId
            } else {
                unmatched += cloud
            }
        }
        for (cloud in unmatched) {
            val candidates = byDimensions[cloud.width to cloud.height].orEmpty().filter { it.id !in claimed }
            val proposal = secondary(cloud, candidates) ?: continue
            proposals += proposal
            claimed += proposal.deviceItemId
        }
        return proposals
    }

    /** Same filename. Confidence then depends on timestamp agreement and dimensions. */
    private fun primary(cloud: CloudItemEntity, sameName: List<DeviceItemEntity>): MatchProposal? {
        if (cloud.filename.isBlank() || sameName.isEmpty()) return null
        val scored = sameName.map { device -> device to score(cloud, device) }
        val best = scored.maxByOrNull { it.second.rank } ?: return null
        val ties = scored.count { it.second.rank == best.second.rank }
        val (device, s) = best
        val confidence = when {
            ties > 1 -> MatchConfidence.AMBIGUOUS
            s.timestamp == TimestampAgreement.EXACT && s.dimensions -> MatchConfidence.EXACT
            s.timestamp == TimestampAgreement.ZONE_SHIFTED && s.dimensions -> MatchConfidence.LIKELY
            s.timestamp == TimestampAgreement.EXACT && !dimensionsKnown(cloud, device) -> MatchConfidence.LIKELY
            s.timestamp == TimestampAgreement.DIFFERENT && s.dimensions && !dimensionsKnown(cloud, device) -> MatchConfidence.AMBIGUOUS
            s.dimensions -> MatchConfidence.AMBIGUOUS // filename + dimensions, timestamp unknown or different
            else -> return null // same name but nothing else agrees: a different file with a common name
        }
        return MatchProposal(
            cloudItemId = cloud.id,
            deviceItemId = device.id,
            confidence = confidence,
            evidence = Evidence(
                method = MatchMethod.PRIMARY,
                filenameMatched = true,
                timestampDeltaMs = s.deltaMs,
                timestamp = s.timestamp,
                dimensionsMatched = s.dimensions,
                candidateCount = sameName.size,
            ),
        )
    }

    /** Renamed files: same dimensions plus a capture time that agrees, or a date in the name that agrees. */
    private fun secondary(cloud: CloudItemEntity, sameDimensions: List<DeviceItemEntity>): MatchProposal? {
        if (sameDimensions.isEmpty() || cloud.width <= 0 || cloud.height <= 0) return null
        val exactTime = sameDimensions.filter { agreement(cloud.createTime, it.dateTaken).first == TimestampAgreement.EXACT }
        if (exactTime.isNotEmpty()) {
            val device = exactTime.first()
            return MatchProposal(
                cloudItemId = cloud.id,
                deviceItemId = device.id,
                confidence = if (exactTime.size == 1) MatchConfidence.LIKELY else MatchConfidence.AMBIGUOUS,
                evidence = Evidence(
                    method = MatchMethod.SECONDARY,
                    filenameMatched = false,
                    timestampDeltaMs = abs(cloud.createTime - device.dateTaken),
                    timestamp = TimestampAgreement.EXACT,
                    dimensionsMatched = true,
                    candidateCount = exactTime.size,
                    note = "renamed: capture time and dimensions agree",
                ),
            )
        }
        val cloudDay = cloud.createTime / ONE_DAY_MS
        val byDate = sameDimensions.filter { device ->
            FilenameDates.extract(device.displayName)?.let { abs(it / ONE_DAY_MS - cloudDay) <= 1 } == true ||
                FilenameDates.extract(cloud.filename)?.let { abs(it / ONE_DAY_MS - device.dateTaken / ONE_DAY_MS) <= 1 } == true
        }
        if (byDate.size != 1) return null
        val device = byDate.single()
        return MatchProposal(
            cloudItemId = cloud.id,
            deviceItemId = device.id,
            confidence = MatchConfidence.AMBIGUOUS,
            evidence = Evidence(
                method = MatchMethod.SECONDARY,
                filenameMatched = false,
                timestampDeltaMs = abs(cloud.createTime - device.dateTaken),
                timestamp = agreement(cloud.createTime, device.dateTaken).first,
                dimensionsMatched = true,
                candidateCount = 1,
                note = "date in filename and dimensions agree",
            ),
        )
    }

    private data class Score(val timestamp: TimestampAgreement, val deltaMs: Long?, val dimensions: Boolean) {
        val rank: Int
            get() = when (timestamp) {
                TimestampAgreement.EXACT -> 4
                TimestampAgreement.ZONE_SHIFTED -> 3
                TimestampAgreement.UNKNOWN -> 2
                TimestampAgreement.DIFFERENT -> 0
            } * 2 + if (dimensions) 1 else 0
    }

    private fun score(cloud: CloudItemEntity, device: DeviceItemEntity): Score {
        val (agreement, delta) = agreement(cloud.createTime, device.dateTaken)
        val dims = dimensionsKnown(cloud, device) && cloud.width == device.width && cloud.height == device.height ||
            dimensionsKnown(cloud, device) && cloud.width == device.height && cloud.height == device.width // EXIF rotation applied on one side only
        return Score(agreement, delta, dims)
    }

    private fun dimensionsKnown(cloud: CloudItemEntity, device: DeviceItemEntity): Boolean =
        cloud.width > 0 && cloud.height > 0 && device.width > 0 && device.height > 0

    fun agreement(cloudTime: Long, deviceTime: Long): Pair<TimestampAgreement, Long?> {
        if (cloudTime <= 0L || deviceTime <= 0L) return TimestampAgreement.UNKNOWN to null
        val delta = abs(cloudTime - deviceTime)
        return when {
            delta <= TIMESTAMP_TOLERANCE_MS -> TimestampAgreement.EXACT
            delta <= MAX_ZONE_SHIFT_MS && (delta % HALF_HOUR_MS) <= TIMESTAMP_TOLERANCE_MS -> TimestampAgreement.ZONE_SHIFTED
            delta <= MAX_ZONE_SHIFT_MS && HALF_HOUR_MS - (delta % HALF_HOUR_MS) <= TIMESTAMP_TOLERANCE_MS -> TimestampAgreement.ZONE_SHIFTED
            else -> TimestampAgreement.DIFFERENT
        } to delta
    }
}

/** Pulls a capture date out of camera and app filename conventions. */
internal object FilenameDates {
    private val patterns = listOf(
        Regex("""(?:^|[^\d])(20\d{2})(\d{2})(\d{2})(?:[^\d]|$)"""), // IMG_20240305_143000, PXL_20240305_..., IMG-20240305-WA0001
        Regex("""(?:^|[^\d])(20\d{2})-(\d{2})-(\d{2})(?:[^\d]|$)"""), // Screenshot_2024-03-05-14-30-00
    )

    /** Epoch millis at UTC midnight of the date in [filename], or null. */
    fun extract(filename: String): Long? {
        for (pattern in patterns) {
            val m = pattern.find(filename) ?: continue
            val (y, mo, d) = m.destructured
            val month = mo.toInt()
            val day = d.toInt()
            if (month !in 1..12 || day !in 1..31) continue
            return try {
                java.time.LocalDate.of(y.toInt(), month, day).toEpochDay() * 24 * 60 * 60_000L
            } catch (_: java.time.DateTimeException) {
                null
            }
        }
        return null
    }
}
