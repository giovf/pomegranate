package com.gankdat.pomegranate.data.model

/** Where a piece of media currently lives. The UI always renders each state with the same colour and icon. */
public enum class MediaState {
    /** On this phone only; Google Photos has no copy the app knows of. */
    DEVICE_ONLY,

    /** In Google Photos only; nothing on this phone. */
    CLOUD_ONLY,

    /** Present in both places. */
    BOTH,
}
