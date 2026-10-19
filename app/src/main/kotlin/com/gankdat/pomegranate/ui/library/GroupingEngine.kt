package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.ui.format.Formatters
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Pure grouping of device items; no Android dependencies so it is unit-testable. */
internal object GroupingEngine {
    const val UNKNOWN_FOLDER: String = "Unknown folder"
    const val UNKNOWN_DATE: String = "Unknown date"

    fun group(items: List<DeviceItemEntity>, mode: GroupingMode, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<MediaGroup> =
        when (mode) {
            GroupingMode.BY_FOLDER -> byFolder(items)
            GroupingMode.BY_MONTH -> byMonth(items, zone, locale)
        }

    /** Folders sorted by name (case-insensitive); items newest first. */
    fun byFolder(items: List<DeviceItemEntity>): List<MediaGroup> =
        items.groupBy { it.bucket.ifBlank { UNKNOWN_FOLDER } }
            .map { (name, members) -> MediaGroup(key = "folder:$name", name = name, items = members.newestFirst()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Months newest first, with undated items last; items newest first within a month. */
    fun byMonth(items: List<DeviceItemEntity>, zone: ZoneId, locale: Locale): List<MediaGroup> {
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

    private fun List<DeviceItemEntity>.newestFirst(): List<DeviceItemEntity> =
        sortedWith(compareByDescending<DeviceItemEntity> { it.dateTaken }.thenByDescending { it.id })
}
