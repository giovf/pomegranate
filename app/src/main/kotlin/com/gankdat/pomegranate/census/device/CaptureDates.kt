package com.gankdat.pomegranate.census.device

import com.gankdat.pomegranate.reconcile.FilenameDates
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

/**
 * Works out when a photo or video was actually taken. Android's own "date
 * taken" is often missing or wrong for files copied from another phone: it
 * then falls back to the moment the file was written, so a whole library
 * lands in the month of the transfer. Pure, so every rule is unit-tested.
 *
 * Order of trust:
 * 1. MediaStore's date when it is clearly older than the file itself (a real capture date).
 * 2. MediaStore's date when the filename's date agrees with it.
 * 3. The file's own metadata (EXIF for photos, container date for videos), read only when needed.
 * 4. A date in the filename (`IMG-20190305-WA0001`, `PXL_20190305_…`), at midday to stay on the right day in any timezone.
 * 5. MediaStore's date as given, else 0 (the caller then falls back to the file date).
 */
internal object CaptureDates {
    /** A "date taken" this close to the file's own modification time may just be the copy time. */
    const val SUSPICIOUS_WINDOW_MS: Long = 2L * 24 * 60 * 60_000
    private const val DAY_MS: Long = 24L * 60 * 60_000
    private const val MIDDAY_MS: Long = 12L * 60 * 60_000

    fun resolve(mediaStoreTaken: Long, fileModified: Long, displayName: String, embedded: () -> Long?): Long {
        val fromStore = mediaStoreTaken.takeIf { it > 0 }
        if (fromStore != null && (fileModified <= 0 || abs(fromStore - fileModified) > SUSPICIOUS_WINDOW_MS)) return fromStore
        val fromName = FilenameDates.extract(displayName)
        if (fromStore != null && fromName != null && abs(fromStore / DAY_MS - fromName / DAY_MS) <= 1) return fromStore
        embedded()?.takeIf { it > 0 }?.let { return it }
        if (fromName != null) return fromName + MIDDAY_MS
        return fromStore ?: 0L
    }

    private val videoDateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'"),
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"),
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
    )

    /** Parses MediaMetadataRetriever's METADATA_KEY_DATE (UTC); null for absent, placeholder (1904/1970) or unparseable values. */
    fun parseVideoDate(text: String?): Long? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        for (format in videoDateFormats) {
            val parsed = try {
                LocalDateTime.parse(value, format)
            } catch (_: DateTimeParseException) {
                continue
            }
            if (parsed.year < 1980) return null // 1904/1970 epoch placeholders written by some encoders
            return parsed.toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return null
    }
}
