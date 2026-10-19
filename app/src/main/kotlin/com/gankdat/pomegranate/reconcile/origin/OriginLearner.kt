package com.gankdat.pomegranate.reconcile.origin

import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource
import java.util.Locale

/** A (folder, filename) pair from the device census; all the learner needs. */
internal data class CensusFile(val bucket: String, val filename: String)

/**
 * Learns folder-to-filename-prefix rules from the phone. A rule is kept when
 * enough files share the prefix and the prefix is specific to one folder:
 * confidence is the share of all files with that prefix that live there.
 */
internal object OriginLearner {
    const val MIN_SUPPORT: Int = 5
    const val MIN_CONFIDENCE: Float = 0.5f
    private val prefixPattern = Regex("""^([A-Za-z][A-Za-z _-]{1,19}?)(?=\d)""")

    fun learn(files: List<CensusFile>): List<OriginRuleEntity> {
        val byPrefix = HashMap<String, MutableMap<String, Int>>() // prefix -> bucket -> count
        for (file in files) {
            val prefix = prefixOf(file.filename) ?: continue
            val bucket = file.bucket.trim()
            if (bucket.isEmpty()) continue
            byPrefix.getOrPut(prefix) { HashMap() }.merge(bucket, 1, Int::plus)
        }
        return byPrefix.flatMap { (prefix, buckets) ->
            val total = buckets.values.sum()
            buckets.mapNotNull { (bucket, count) ->
                val confidence = count.toFloat() / total
                if (count < MIN_SUPPORT || confidence < MIN_CONFIDENCE) return@mapNotNull null
                OriginRuleEntity(
                    source = OriginSource.LEARNED,
                    pattern = patternFor(prefix),
                    originLabel = BuiltinPatterns.labelForBucket(bucket),
                    confidence = confidence,
                )
            }
        }.sortedWith(compareByDescending<OriginRuleEntity> { it.confidence }.thenBy { it.pattern })
    }

    /** Letters and separators before the first digit, lowercased: `IMG_` for `IMG_20240305_1.jpg`, `Screenshot_` for screenshots. */
    fun prefixOf(filename: String): String? = prefixPattern.find(filename)?.groupValues?.get(1)?.lowercase(Locale.ROOT)

    /** Anchored, case-insensitive regex for a prefix followed by digits. */
    fun patternFor(prefix: String): String = "(?i)^" + Regex.escape(prefix) + """\d"""
}
