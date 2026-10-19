package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.json.Json
import com.gankdat.pomegranate.data.json.JsonException
import com.gankdat.pomegranate.data.json.bool
import com.gankdat.pomegranate.data.json.jsonOf
import com.gankdat.pomegranate.data.json.long
import com.gankdat.pomegranate.data.json.string

internal enum class MatchMethod { PRIMARY, SECONDARY, HASH_CONFIRMED }

/** How the device and Google timestamps relate. */
internal enum class TimestampAgreement {
    /** Within one second. */
    EXACT,

    /** Differ by a whole number of half-hours, as happens when one side applied a timezone. */
    ZONE_SHIFTED,

    /** One side has no usable timestamp. */
    UNKNOWN,

    /** Genuinely different. */
    DIFFERENT,
}

/** Why a cloud item and a device item were paired; stored as JSON in `match.evidence` and shown to the user. */
internal data class Evidence(
    val method: MatchMethod,
    val filenameMatched: Boolean,
    val timestampDeltaMs: Long?,
    val timestamp: TimestampAgreement,
    val dimensionsMatched: Boolean,
    /** Null until a hash comparison has been made. */
    val hashMatched: Boolean? = null,
    /** How many device candidates competed; more than one lowers confidence. */
    val candidateCount: Int = 1,
    val note: String? = null,
) {
    fun toJson(): String = jsonOf(
        "method" to method.name,
        "filenameMatched" to filenameMatched,
        "timestampDeltaMs" to timestampDeltaMs,
        "timestamp" to timestamp.name,
        "dimensionsMatched" to dimensionsMatched,
        "hashMatched" to hashMatched,
        "candidateCount" to candidateCount.toLong(),
        "note" to note,
    )

    companion object {
        /** Null for unreadable or foreign JSON rather than throwing; the match row is still valid. */
        fun fromJson(json: String): Evidence? = try {
            val o = Json.parseObject(json)
            Evidence(
                method = o.string("method")?.let { name -> MatchMethod.entries.firstOrNull { it.name == name } } ?: return null,
                filenameMatched = o.bool("filenameMatched") ?: false,
                timestampDeltaMs = o.long("timestampDeltaMs"),
                timestamp = o.string("timestamp")?.let { name -> TimestampAgreement.entries.firstOrNull { it.name == name } } ?: TimestampAgreement.UNKNOWN,
                dimensionsMatched = o.bool("dimensionsMatched") ?: false,
                hashMatched = o.bool("hashMatched"),
                candidateCount = o.long("candidateCount")?.toInt() ?: 1,
                note = o.string("note"),
            )
        } catch (_: JsonException) {
            null
        }
    }
}
