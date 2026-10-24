package com.gankdat.pomegranate.data.db.entity

/** Query result (not a table): how many indexed items a picker round holds and the span of their dates. */
internal data class SessionRange(
    val sessionId: String,
    val itemCount: Int,
    /** Epoch millis of the oldest dated item, null if none is dated. */
    val oldest: Long?,
    val newest: Long?,
)
