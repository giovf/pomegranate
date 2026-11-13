package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.ui.format.Formatters
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Pure grouping of library entries; no Android dependencies so it is unit-testable. */
internal object GroupingEngine {
    const val UNKNOWN_FOLDER: String = "Unknown folder"
    const val CLOUD_UNKNOWN_FOLDER: String = "Only in Google Photos"
    const val CLOUD_FOLDER_PREFIX: String = "Only in Google Photos: "
    const val UNKNOWN_DATE: String = "Unknown date"

    fun group(items: List<LibraryEntry>, mode: GroupingMode, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<MediaGroup> =
        when (mode) {
            GroupingMode.BY_FOLDER -> byFolder(items)
            GroupingMode.BY_MONTH -> byMonth(items, zone, locale)
        }

    /**
     * Phone folders sorted by name (case-insensitive), then Google-only items
     * grouped by their likely origin. The two never share a group: a phone
     * "Camera" folder and Google-only photos guessed to come from a camera are
     * different things. Items newest first.
     */
    fun byFolder(items: List<LibraryEntry>): List<MediaGroup> {
        val (phone, cloud) = items.partition { it.deviceItemId != null }
        val phoneGroups = phone.groupBy { it.bucket ?: UNKNOWN_FOLDER }
            .map { (name, members) -> MediaGroup(key = "folder:$name", name = name, items = members.newestFirst()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val cloudGroups = cloud.groupBy { it.bucket }
            .map { (origin, members) ->
                val name = origin?.let { CLOUD_FOLDER_PREFIX + it } ?: CLOUD_UNKNOWN_FOLDER
                MediaGroup(key = "cloud:${origin.orEmpty()}", name = name, items = members.newestFirst())
            }
            .sortedWith(compareBy<MediaGroup> { it.name == CLOUD_UNKNOWN_FOLDER }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return phoneGroups + cloudGroups
    }

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

    /**
     * Splits a group's items into runs taken on the same calendar day, keeping
     * their order, so the grid can show a date separator above each run.
     * Undated items form one run labelled [UNKNOWN_DATE].
     */
    fun byDay(items: List<LibraryEntry>, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<DaySection> {
        val sections = mutableListOf<DaySection>()
        var currentDay: Long? = null
        var run = mutableListOf<LibraryEntry>()
        fun flush() {
            val day = currentDay ?: return
            val label = if (day == UNDATED_DAY) UNKNOWN_DATE else Formatters.day(run.first().dateTaken, zone, locale)
            sections += DaySection(key = if (day == UNDATED_DAY) "unknown" else day.toString(), label = label, items = run)
        }
        for (item in items) {
            val day = if (item.dateTaken > 0) Instant.ofEpochMilli(item.dateTaken).atZone(zone).toLocalDate().toEpochDay() else UNDATED_DAY
            if (day != currentDay) {
                flush()
                currentDay = day
                run = mutableListOf()
            }
            run += item
        }
        flush()
        return sections
    }

    private const val UNDATED_DAY = Long.MIN_VALUE

    private fun List<LibraryEntry>.newestFirst(): List<LibraryEntry> =
        sortedWith(compareByDescending<LibraryEntry> { it.dateTaken }.thenByDescending { it.key })
}
