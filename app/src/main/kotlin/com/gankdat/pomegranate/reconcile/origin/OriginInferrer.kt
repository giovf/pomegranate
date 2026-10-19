package com.gankdat.pomegranate.reconcile.origin

import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource

/**
 * Picks an origin for a filename. Learned rules win when confident because
 * they reflect this phone's own habits; built-in rules cover the rest.
 * Results under [MIN_CONFIDENCE] are dropped so the file lands in the plain
 * month folder instead of a wrong app folder.
 */
internal class OriginInferrer(learnedRules: List<OriginRuleEntity>) {
    private val learned = learnedRules
        .filter { it.source == OriginSource.LEARNED }
        .mapNotNull { rule -> runCatching { Regex(rule.pattern) }.getOrNull()?.let { it to rule } }
        .sortedByDescending { it.second.confidence }

    fun infer(filename: String, bucket: String? = null): OriginResult? {
        val candidates = buildList {
            learned.firstOrNull { (regex, rule) -> rule.confidence >= LEARNED_MIN_CONFIDENCE && regex.containsMatchIn(filename) }
                ?.let { (_, rule) -> add(OriginResult(rule.originLabel, rule.confidence, OriginSource.LEARNED)) }
            BuiltinPatterns.matchFilename(filename)?.let { add(OriginResult(it.origin, BuiltinPatterns.FILENAME_CONFIDENCE, OriginSource.BUILTIN)) }
            bucket?.let { b -> BuiltinPatterns.matchBucket(b)?.let { add(OriginResult(it.origin, BuiltinPatterns.BUCKET_CONFIDENCE, OriginSource.BUILTIN)) } }
        }
        // Highest confidence wins; learned beats built-in on a tie.
        return candidates.maxWithOrNull(compareBy<OriginResult> { it.confidence }.thenBy { it.source == OriginSource.LEARNED })
            ?.takeIf { it.confidence >= MIN_CONFIDENCE }
    }

    companion object {
        const val MIN_CONFIDENCE: Float = 0.5f
        const val LEARNED_MIN_CONFIDENCE: Float = 0.7f
    }
}
