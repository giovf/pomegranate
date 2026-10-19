package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.data.json.Json
import com.gankdat.pomegranate.data.json.JsonException
import com.gankdat.pomegranate.data.json.JsonObject
import com.gankdat.pomegranate.data.json.bool
import com.gankdat.pomegranate.data.json.long
import com.gankdat.pomegranate.data.json.obj
import com.gankdat.pomegranate.data.json.objects
import com.gankdat.pomegranate.data.json.requireString
import com.gankdat.pomegranate.data.json.string
import java.time.Instant
import java.time.format.DateTimeParseException

/** Pure parsing of Picker API JSON; field names verified against the v1 REST reference. */
internal object PickerParser {
    const val DEFAULT_POLL_INTERVAL_MILLIS: Long = 5_000
    const val DEFAULT_TIMEOUT_MILLIS: Long = 30 * 60_000

    fun session(body: String): PickerSession = session(Json.parseObject(body))

    fun session(json: JsonObject): PickerSession {
        val polling = json.obj("pollingConfig")
        return PickerSession(
            id = json.requireString("id"),
            pickerUri = json.string("pickerUri").orEmpty(),
            pollIntervalMillis = polling?.string("pollInterval")?.let(::durationMillis) ?: DEFAULT_POLL_INTERVAL_MILLIS,
            timeoutMillis = polling?.string("timeoutIn")?.let(::durationMillis) ?: DEFAULT_TIMEOUT_MILLIS,
            expireTimeMillis = json.string("expireTime")?.let(::timestampMillis),
            mediaItemsSet = json.bool("mediaItemsSet") ?: false,
        )
    }

    fun mediaItemsPage(body: String): MediaItemsPage {
        val json = Json.parseObject(body)
        return MediaItemsPage(
            items = json.objects("mediaItems").map(::mediaItem),
            nextPageToken = json.string("nextPageToken")?.takeIf { it.isNotEmpty() },
        )
    }

    fun mediaItem(json: JsonObject): PickedMediaItem {
        val file = json.obj("mediaFile") ?: throw JsonException("media item without mediaFile")
        val meta = file.obj("mediaFileMetadata")
        return PickedMediaItem(
            id = json.requireString("id"),
            createTimeMillis = json.string("createTime")?.let(::timestampMillis) ?: 0L,
            type = json.string("type") ?: "TYPE_UNSPECIFIED",
            baseUrl = file.requireString("baseUrl"),
            mimeType = file.string("mimeType").orEmpty(),
            filename = file.string("filename").orEmpty(),
            width = meta?.long("width")?.toInt() ?: 0,
            height = meta?.long("height")?.toInt() ?: 0,
            cameraMake = meta?.string("cameraMake"),
            cameraModel = meta?.string("cameraModel"),
        )
    }

    /** Protobuf `Duration` JSON form: decimal seconds with an `s` suffix, e.g. `5s`, `0.5s`, `1800s`. */
    fun durationMillis(text: String): Long? {
        val seconds = text.trim().removeSuffix("s").toDoubleOrNull() ?: return null
        return (seconds * 1_000).toLong()
    }

    /** RFC 3339 timestamp to epoch millis; null when unparseable. */
    fun timestampMillis(text: String): Long? = try {
        Instant.parse(text).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
