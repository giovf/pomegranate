package com.gankdat.pomegranate.data.model

/**
 * The user's verdict on one uncertain phone/Google pair. Keyed by ids that
 * survive rescans and re-indexing (Google's media item id; the phone file's
 * MediaStore volume and id) rather than database row ids.
 */
public data class MatchDecision(
    val pickerMediaItemId: String,
    val volume: String,
    val mediaStoreId: Long,
    /** True: the same photo or video. False: different, never pair them again. */
    val same: Boolean,
)
