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

    /** [rejected] holds (cloud id, device id) pairs the user said are different; they are never proposed. */
    fun match(cloudItems: List<CloudItemEntity>, deviceItems: List<DeviceItemEntity>, rejected: Set<Pair<Long, Long>> = emptySet()): List<MatchProposal> {
        val byName = deviceItems.groupBy { it.displayName.lowercase(Locale.ROOT) }
        val byBase = deviceItems.groupBy { baseKey(it.displayName, it.mime) }
        val byDimensions = deviceItems.filter { it.width > 0 && it.height > 0 }.groupBy { it.width to it.height }
        val claimed = HashSet<Long>()
        val proposals = ArrayList<MatchProposal>()

        // Primary pass first for every cloud item so exact pairs claim their device rows before looser rules run.
        val unmatched = ArrayList<CloudItemEntity>()
        for (cloud in cloudItems) {
            fun open(device: DeviceItemEntity) = device.id !in claimed && (cloud.id to device.id) !in rejected
            val sameName = byName[cloud.filename.lowercase(Locale.ROOT)].orEmpty().filter(::open)
            val proposal = if (sameName.isNotEmpty()) {
                primary(cloud, sameName, formatChanged = false)
            } else {
                // Same name in another format of the same kind (HEIC on one side, JPG on the other).
                primary(cloud, byBase[baseKey(cloud.filename, cloud.mime)].orEmpty().filter(::open), formatChanged = true)
            }
            if (proposal != null) {
                proposals += proposal
                claimed += proposal.deviceItemId
            } else {
                unmatched += cloud
            }
        }
        for (cloud in unmatched) {
            val kind = cloud.mime.substringBefore('/')
            val candidates = byDimensions[cloud.width to cloud.height].orEmpty()
                .filter { it.id !in claimed && (cloud.id to it.id) !in rejected && it.mime.substringBefore('/') == kind }
            val proposal = secondary(cloud, candidates) ?: continue
            proposals += proposal
            claimed += proposal.deviceItemId
        }
        return proposals
    }

    /**
     * Same filename (or, with [formatChanged], same name in another format).
     * Confidence then depends on timestamp agreement and dimensions. Unknown
     * dimensions on either side do not count against a match.
     */
    private fun primary(cloud: CloudItemEntity, sameName: List<DeviceItemEntity>, formatChanged: Boolean): MatchProposal? {
        if (cloud.filename.isBlank() || sameName.isEmpty()) return null
        val scored = sameName.map { device -> device to score(cloud, device) }
        val best = scored.maxByOrNull { it.second.rank } ?: return null
        val ties = scored.count { it.second.rank == best.second.rank }
        val (device, s) = best
        val dimsOk = s.dimensions || !dimensionsKnown(cloud, device)
        val distinctive = sameName.size == 1 && isDistinctive(cloud.filename)
        val timeAgrees = s.timestamp == TimestampAgreement.EXACT || s.timestamp == TimestampAgreement.ZONE_SHIFTED
        val raw = when {
            ties > 1 -> MatchConfidence.AMBIGUOUS
            s.timestamp == TimestampAgreement.EXACT && dimsOk -> if (s.dimensions) MatchConfidence.EXACT else MatchConfidence.LIKELY
            s.timestamp == TimestampAgreement.ZONE_SHIFTED && dimsOk -> MatchConfidence.LIKELY
            // Same name and capture time but another pixel size: one side is a resized copy. Safe to call it
            // present only when the phone holds the larger one (Google's storage saver shrinks its copy);
            // a smaller or cropped phone copy goes to the user, so a better original is never skipped.
            timeAgrees && s.resize == Resize.PHONE_LARGER -> MatchConfidence.LIKELY
            timeAgrees -> MatchConfidence.AMBIGUOUS
            // Phone date unknown or only day-precise: a distinctive name plus agreeing size is strong evidence.
            s.timestamp == TimestampAgreement.UNKNOWN && dimsOk && distinctive -> MatchConfidence.LIKELY
            s.timestamp == TimestampAgreement.UNKNOWN && dimsOk -> MatchConfidence.AMBIGUOUS
            s.timestamp == TimestampAgreement.UNKNOWN && distinctive && s.resize != Resize.OTHER_SHAPE -> MatchConfidence.AMBIGUOUS
            s.timestamp == TimestampAgreement.DIFFERENT && s.dimensions -> MatchConfidence.AMBIGUOUS
            else -> return null // same name but nothing else agrees: a different file with a common name
        }
        val confidence = if (formatChanged && raw == MatchConfidence.EXACT) MatchConfidence.LIKELY else raw
        return MatchProposal(
            cloudItemId = cloud.id,
            deviceItemId = device.id,
            confidence = confidence,
            evidence = Evidence(
                method = MatchMethod.PRIMARY,
                filenameMatched = !formatChanged,
                timestampDeltaMs = s.deltaMs,
                timestamp = s.timestamp,
                dimensionsMatched = s.dimensions,
                candidateCount = sameName.size,
                note = when {
                    formatChanged -> "same name, different file format"
                    dimsOk -> null
                    s.resize == Resize.PHONE_LARGER -> "same name; the copy on this phone is larger than the one in Google Photos"
                    s.resize == Resize.PHONE_SMALLER -> "same name; the copy on this phone is smaller than the one in Google Photos"
                    else -> "same name; the two copies have different shapes (cropped or edited)"
                },
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
        if (byDate.size != 1) return nearInTime(cloud, sameDimensions)
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

    /**
     * Same dimensions and a real capture time within a minute, with nothing else that close:
     * some apps stamp the end of a recording or the moment of saving. Only ever a suggestion
     * for the user to check, and never offered when a burst leaves several candidates.
     */
    private fun nearInTime(cloud: CloudItemEntity, sameDimensions: List<DeviceItemEntity>): MatchProposal? {
        if (cloud.createTime <= 0) return null
        val near = sameDimensions.filter { deviceDateKnown(it) && !isDayPrecise(it.dateTaken) && abs(cloud.createTime - it.dateTaken) <= NEAR_TIME_MS }
        val device = near.singleOrNull() ?: return null
        return MatchProposal(
            cloudItemId = cloud.id,
            deviceItemId = device.id,
            confidence = MatchConfidence.AMBIGUOUS,
            evidence = Evidence(
                method = MatchMethod.SECONDARY,
                filenameMatched = false,
                timestampDeltaMs = abs(cloud.createTime - device.dateTaken),
                timestamp = TimestampAgreement.DIFFERENT,
                dimensionsMatched = true,
                candidateCount = 1,
                note = "renamed: dimensions agree and taken within a minute",
            ),
        )
    }

    private const val NEAR_TIME_MS = 60_000L

    /** How two known, unequal pixel sizes relate. */
    private enum class Resize { PHONE_LARGER, PHONE_SMALLER, OTHER_SHAPE }

    /** Null when the sizes are equal (in either orientation) or unknown on a side. Same shape means aspect ratios within 2%. */
    private fun resize(cloud: CloudItemEntity, device: DeviceItemEntity): Resize? {
        if (!dimensionsKnown(cloud, device)) return null
        val cloudLong = maxOf(cloud.width, cloud.height).toLong()
        val cloudShort = minOf(cloud.width, cloud.height).toLong()
        val deviceLong = maxOf(device.width, device.height).toLong()
        val deviceShort = minOf(device.width, device.height).toLong()
        if (cloudLong == deviceLong && cloudShort == deviceShort) return null
        val sameShape = abs(cloudLong * deviceShort - deviceLong * cloudShort) * ASPECT_TOLERANCE_INVERSE <= cloudLong * deviceShort
        return when {
            !sameShape -> Resize.OTHER_SHAPE
            deviceLong >= cloudLong && deviceShort >= cloudShort -> Resize.PHONE_LARGER
            else -> Resize.PHONE_SMALLER
        }
    }

    private const val ASPECT_TOLERANCE_INVERSE = 50L // 2%

    private data class Score(val timestamp: TimestampAgreement, val deltaMs: Long?, val dimensions: Boolean, val resize: Resize? = null) {
        val rank: Int
            get() = when (timestamp) {
                TimestampAgreement.EXACT -> 4
                TimestampAgreement.ZONE_SHIFTED -> 3
                TimestampAgreement.UNKNOWN -> 2
                TimestampAgreement.DIFFERENT -> 0
            } * 2 + if (dimensions) 1 else 0
    }

    /** Camera and app filenames carry counters or dates; "image.jpg" does not. */
    private fun isDistinctive(filename: String): Boolean = filename.count { it.isDigit() } >= DISTINCTIVE_DIGITS

    private const val DISTINCTIVE_DIGITS = 4

    /** The scanner stores the file's write time when no capture date exists; such a date says nothing about the photo. */
    private fun deviceDateKnown(device: DeviceItemEntity): Boolean = device.dateTaken != device.dateModified

    /** Name without extension plus media kind, so a photo never pairs with a video of the same name. */
    private fun baseKey(filename: String, mime: String): String =
        filename.substringBeforeLast('.').lowercase(Locale.ROOT) + "|" + mime.substringBefore('/').lowercase(Locale.ROOT)

    /** Filename-derived dates are stored at 12:00 UTC: they are only accurate to the day. */
    private fun isDayPrecise(millis: Long): Boolean = millis > 0 && millis % ONE_DAY_MS == MIDDAY_MS

    private const val MIDDAY_MS = 12 * 60 * 60_000L

    private fun score(cloud: CloudItemEntity, device: DeviceItemEntity): Score {
        val (agreement, delta) = when {
            !deviceDateKnown(device) -> TimestampAgreement.UNKNOWN to null
            isDayPrecise(device.dateTaken) && cloud.createTime > 0 -> {
                val days = abs(cloud.createTime / ONE_DAY_MS - device.dateTaken / ONE_DAY_MS)
                (if (days <= 1) TimestampAgreement.UNKNOWN else TimestampAgreement.DIFFERENT) to abs(cloud.createTime - device.dateTaken)
            }
            else -> agreement(cloud.createTime, device.dateTaken)
        }
        val dims = dimensionsKnown(cloud, device) && cloud.width == device.width && cloud.height == device.height ||
            dimensionsKnown(cloud, device) && cloud.width == device.height && cloud.height == device.width // EXIF rotation applied on one side only
        return Score(agreement, delta, dims, resize(cloud, device))
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
