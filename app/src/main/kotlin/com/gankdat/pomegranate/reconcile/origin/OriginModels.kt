package com.gankdat.pomegranate.reconcile.origin

import com.gankdat.pomegranate.data.db.entity.OriginSource

/** Where a file most likely came from, with how sure the inferrer is. */
internal data class OriginResult(
    val origin: String,
    /** 0.0 to 1.0. */
    val confidence: Float,
    val source: OriginSource,
)

/** A built-in rule for a well-known camera or messaging app. */
internal data class BuiltinRule(
    val origin: String,
    val filenamePatterns: List<Regex>,
    val bucketPatterns: List<Regex>,
)
