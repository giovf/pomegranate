package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupingEngineTest {
    private val march = 1_709_649_000_000L // 2024-03-05
    private val april = 1_712_241_000_000L // 2024-04-04

    private fun item(id: Long, bucket: String, dateTaken: Long, size: Long = 100) = DeviceItemEntity(
        id = id,
        mediaStoreId = id,
        volume = "external_primary",
        relativePath = "DCIM/$bucket/",
        bucket = bucket,
        displayName = "IMG_$id.jpg",
        mime = "image/jpeg",
        size = size,
        dateTaken = dateTaken,
        dateModified = dateTaken,
        width = 10,
        height = 10,
        fingerprint = "fp$id",
    )

    private val items = listOf(
        item(1, "Camera", march, size = 10),
        item(2, "camera2", april, size = 20),
        item(3, "Camera", april, size = 30),
        item(4, "", 0, size = 40),
        item(5, "Screenshots", march + 1, size = 50),
    )

    @Test
    fun `folders sort case-insensitively with blank buckets labelled and items newest first`() {
        val groups = GroupingEngine.byFolder(items)

        assertEquals(listOf("Camera", "camera2", "Screenshots", GroupingEngine.UNKNOWN_FOLDER), groups.map { it.name })
        val camera = groups.first()
        assertEquals(listOf(3L, 1L), camera.items.map { it.id })
        assertEquals(2, camera.itemCount)
        assertEquals(40L, camera.totalSizeBytes)
        assertEquals("folder:Camera", camera.key)
    }

    @Test
    fun `months sort newest first with undated items last`() {
        val groups = GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK)

        assertEquals(listOf("April 2024", "March 2024", GroupingEngine.UNKNOWN_DATE), groups.map { it.name })
        assertEquals(listOf(3L, 2L), groups[0].items.map { it.id })
        assertEquals(listOf(5L, 1L), groups[1].items.map { it.id })
        assertEquals(listOf(4L), groups[2].items.map { it.id })
        assertEquals("month:2024-04", groups[0].key)
    }

    @Test
    fun `group dispatches on mode`() {
        assertEquals(GroupingEngine.byFolder(items), GroupingEngine.group(items, GroupingMode.BY_FOLDER, ZoneOffset.UTC, Locale.UK))
        assertEquals(GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK), GroupingEngine.group(items, GroupingMode.BY_MONTH, ZoneOffset.UTC, Locale.UK))
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<MediaGroup>(), GroupingEngine.byFolder(emptyList()))
        assertEquals(emptyList<MediaGroup>(), GroupingEngine.byMonth(emptyList(), ZoneOffset.UTC, Locale.UK))
    }
}
