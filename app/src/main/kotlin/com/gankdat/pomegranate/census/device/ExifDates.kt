package com.gankdat.pomegranate.census.device

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Parses EXIF date strings; pure so the edge cases are unit-testable. */
internal object ExifDates {
    private val exifFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Converts an EXIF `DateTimeOriginal` (`yyyy:MM:dd HH:mm:ss`) plus an
     * optional `OffsetTimeOriginal` (`+HH:MM`) to epoch millis. Without an
     * offset the camera's local time is interpreted in [zone]. Returns null
     * for blank, placeholder (`0000:00:00 00:00:00`) or malformed values.
     */
    fun toEpochMillis(dateTime: String?, offset: String? = null, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = dateTime?.trim().orEmpty()
        if (text.isEmpty() || text.startsWith("0000")) return null
        val local = try {
            LocalDateTime.parse(text, exifFormat)
        } catch (_: DateTimeParseException) {
            return null
        }
        val zoneOrOffset: ZoneId = offset?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                ZoneOffset.of(it)
            } catch (_: java.time.DateTimeException) {
                null
            }
        } ?: zone
        return local.atZone(zoneOrOffset).toInstant().toEpochMilli()
    }
}
