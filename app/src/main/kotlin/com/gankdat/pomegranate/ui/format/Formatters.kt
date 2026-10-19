package com.gankdat.pomegranate.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Pure, locale-aware formatting helpers shared by the screens. */
internal object Formatters {
    private const val UNIT = 1_000.0

    /** Decimal units (1 MB = 1,000,000 bytes), matching what Android's file managers show. */
    fun fileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < UNIT) return "$bytes B"
        var value = bytes / UNIT
        var unit = "kB"
        for (next in listOf("MB", "GB", "TB")) {
            if (value < UNIT) break
            value /= UNIT
            unit = next
        }
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(locale, pattern, value, unit)
    }

    /** `m:ss`, or `h:mm:ss` from one hour. */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1_000).coerceAtLeast(0)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** `March 2024` style month label. */
    fun monthYear(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
