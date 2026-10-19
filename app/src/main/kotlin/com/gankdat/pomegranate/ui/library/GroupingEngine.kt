package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.ui.format.Formatters
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Pure grouping of library entries; no Android dependencies so it is unit-testable. */
internal object GroupingEngine {
    const val UNKNOWN_FOLDER: String = "Unknown folder"
    const val CLOUD_UNKNOWN_FOLDER: String = "Google Photos (origin unknown)"
    const val UNKNOWN_DATE: String = "Unknown date"

    fun group(items: List<LibraryEntry>, mode: GroupingMode, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<MediaGroup> =
        when (mode) {
            GroupingMode.BY_FOLDER -> byFolder(items)
            GroupingMode.BY_MONTH -> byMonth(items, zone, locale)
        }

    /** Folders sorted by name (case-insensitive); items newest first. Cloud-only items group under their origin. */
    fun byFolder(items: List<LibraryEntry>): List<MediaGroup> =
        items.groupBy { it.bucket ?: if (it.deviceItemId != null) UNKNOWN_FOLDER else CLOUD_UNKNOWN_FOLDER }
            .map { (name, members) -> MediaGroup(key = "folder:$name", name = name, items = members.newestFirst()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Months newest first, with undated items last; items newest first within a month. */
    fun byMonth(items: List<LibraryEntry>, zone: ZoneId, locale: Locale): List<MediaGroup> {
        val (dated, undated) = items.partition { it.dateTaken > 0 }
        val months = dated.groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTaken).atZone(zone)) }
            .toSortedMap(compareByDescending { it })
            .map { (month, members) ->
                MediaGroup(
                    key = "month:$month",
                    name = Formatters.monthYear(members.first().dateTaken, zone, locale),
                    items = members.newestFirst(),
                )
            }
        return if (undated.isEmpty()) months else months + MediaGroup(key = "month:unknown", name = UNKNOWN_DATE, items = undated.newestFirst())
    }

    private fun List<LibraryEntry>.newestFirst(): List<LibraryEntry> =
        sortedWith(compareByDescending<LibraryEntry> { it.dateTaken }.thenByDescending { it.key })
}
