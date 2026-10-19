package com.gankdat.pomegranate.census.cloud

/** Scriptable in-memory Picker API. */
internal class FakePickerApi : PickerApi {
    val sessions = linkedMapOf<String, PickerSession>()
    val items = linkedMapOf<String, MutableList<PickedMediaItem>>()
    val deleted = mutableListOf<String>()
    var pageSizeUsed = 0
    var nextSessionId = 1
    var getSessionHook: (suspend (String) -> Unit)? = null
    var listCalls = 0

    fun session(id: String, mediaItemsSet: Boolean = false, pollInterval: Long = 1_000, timeout: Long = 60_000) =
        PickerSession(id, "https://photos.google.com/picker/$id", pollInterval, timeout, null, mediaItemsSet)

    override suspend fun createSession(): PickerSession {
        val s = session("s${nextSessionId++}")
        sessions[s.id] = s
        return s
    }

    override suspend fun getSession(sessionId: String): PickerSession {
        getSessionHook?.invoke(sessionId)
        return sessions[sessionId] ?: throw PickerApiException(404, "no session")
    }

    override suspend fun listMediaItems(sessionId: String, pageToken: String?, pageSize: Int): MediaItemsPage {
        listCalls++
        pageSizeUsed = pageSize
        if (sessionId !in sessions) throw PickerApiException(404, "no session")
        val all = items[sessionId].orEmpty()
        val start = pageToken?.toInt() ?: 0
        val end = minOf(start + pageSize, all.size)
        return MediaItemsPage(all.subList(start, end), if (end < all.size) end.toString() else null)
    }

    override suspend fun deleteSession(sessionId: String) {
        deleted += sessionId
        if (sessions.remove(sessionId) == null) throw PickerApiException(404, "no session")
    }
}

internal fun picked(id: String, filename: String = "$id.jpg", baseUrl: String = "https://lh3/$id") = PickedMediaItem(
    id = id,
    createTimeMillis = 1_700_000_000_000,
    type = "PHOTO",
    baseUrl = baseUrl,
    mimeType = "image/jpeg",
    filename = filename,
    width = 4_000,
    height = 3_000,
    cameraMake = null,
    cameraModel = null,
)
