package com.gankdat.pomegranate.census.cloud

/** A Google Photos Picker session as returned by `sessions.create` / `sessions.get`. */
internal data class PickerSession(
    val id: String,
    val pickerUri: String,
    val pollIntervalMillis: Long,
    val timeoutMillis: Long,
    /** Epoch millis when Google will discard the session, null if not reported. */
    val expireTimeMillis: Long?,
    val mediaItemsSet: Boolean,
)

/** One item the user picked; `durationMs` is absent because the Picker API does not report it. */
internal data class PickedMediaItem(
    val id: String,
    val createTimeMillis: Long,
    /** `PHOTO`, `VIDEO` or `TYPE_UNSPECIFIED`. */
    val type: String,
    val baseUrl: String,
    val mimeType: String,
    val filename: String,
    val width: Int,
    val height: Int,
    val cameraMake: String?,
    val cameraModel: String?,
)

internal data class MediaItemsPage(val items: List<PickedMediaItem>, val nextPageToken: String?)

/** HTTP-level failure from the Picker API. */
internal class PickerApiException(val httpStatus: Int, message: String) : Exception("Picker API $httpStatus: $message") {
    val isAuthFailure: Boolean get() = httpStatus == 401 || httpStatus == 403
    val isGone: Boolean get() = httpStatus == 404 || httpStatus == 400
}

/** The user is not signed in, or the session could not be refreshed. */
internal class PickerAuthException : Exception("No valid Google access token")

internal sealed interface PollResult {
    data class Complete(val session: PickerSession) : PollResult

    data object Timeout : PollResult

    data class Failed(val message: String) : PollResult
}
