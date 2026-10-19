package com.gankdat.pomegranate.data.db.entity

/** How sibling files relate inside a [MediaGroupEntity]. */
internal enum class MediaGroupKind {
    /** A lone file with no siblings. */
    SINGLE,

    /** iOS-style Live Photo: a still plus a short video clip. */
    LIVE_PHOTO,

    /** Android motion photo: a still plus its embedded or separate video half. */
    MOTION_PHOTO,

    /** RAW capture alongside its camera-produced JPEG. */
    RAW_PAIR,

    /** An original alongside an edited copy. */
    EDIT_PAIR,
}

/** Strength of the evidence that a cloud item and a device item are the same media. */
internal enum class MatchConfidence {
    /** Same filename, size, timestamp and dimensions, or an identical hash. */
    EXACT,

    /** Most fingerprint fields agree; one is missing or differs in a way Google is known to alter. */
    LIKELY,

    /** More than one candidate, or too little evidence to decide automatically. */
    AMBIGUOUS,
}

/** Lifecycle of a single cloud-to-device copy. */
internal enum class TransferState {
    QUEUED,
    RUNNING,

    /** Bytes written; size and hash are being checked before the job is reported as saved. */
    VERIFYING,
    DONE,
    FAILED,
    PAUSED,
}

/**
 * What the pulled file lost on the way out of Google Photos. Shown to the
 * user on every item; a transcoded video is never described as an original.
 */
internal enum class Fidelity {
    ORIGINAL,

    /** Photo bytes with EXIF intact but GPS removed by the Picker API. */
    GPS_STRIPPED,

    /** Video re-encoded by Google; not the original upload. */
    TRANSCODED,
}

/** Where an [OriginRuleEntity] came from. */
internal enum class OriginSource {
    /** Learned from folder-to-filename patterns found in this phone's own media. */
    LEARNED,

    /** Shipped with the app for well-known camera and messaging apps. */
    BUILTIN,
}

/** State of one Google Photos Picker session as tracked locally. */
internal enum class PickerRoundState {
    /** Session created; waiting for the user to finish selecting. */
    ACTIVE,

    /** Items listed and stored in `cloud_item`. */
    INDEXED,

    /** Google-side session or its base URLs have expired. */
    EXPIRED,
}
