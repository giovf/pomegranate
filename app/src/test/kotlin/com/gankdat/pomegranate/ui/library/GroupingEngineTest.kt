package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.model.MediaState
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

internal fun entry(id: Long, bucket: String?, dateTaken: Long, size: Long? = 100, cloud: Boolean = false) = LibraryEntry(
    key = if (cloud) "c:$id" else "d:$id",
    state = if (cloud) MediaState.CLOUD_ONLY else MediaState.DEVICE_ONLY,
    deviceItemId = if (cloud) null else id,
    cloudItemId = if (cloud) id else null,
    displayName = "IMG_$id.jpg",
    bucket = bucket,
    dateTaken = dateTaken,
    sizeBytes = size,
    mime = "image/jpeg",
    durationMs = null,
    thumbnail = if (cloud) ThumbnailSource.Remote("https://lh3/$id") else ThumbnailSource.Local("external_primary", id),
)

class GroupingEngineTest {
    private val march = 1_709_649_000_000L // 2024-03-05
    private val april = 1_712_241_000_000L // 2024-04-04

    private val items = listOf(
        entry(1, "Camera", march, size = 10),
        entry(2, "camera2", april, size = 20),
        entry(3, "Camera", april, size = 30),
        entry(4, null, 0, size = 40),
        entry(5, "Screenshots", march + 1, size = 50),
        entry(6, null, april, size = null, cloud = true),
    )

    @Test
    fun `folders sort case-insensitively, unknown buckets are labelled per side, items newest first`() {
        val groups = GroupingEngine.byFolder(items)

        assertEquals(listOf("Camera", "camera2", GroupingEngine.CLOUD_UNKNOWN_FOLDER, "Screenshots", GroupingEngine.UNKNOWN_FOLDER), groups.map { it.name })
        val camera = groups.first()
        assertEquals(listOf("d:3", "d:1"), camera.items.map { it.key })
        assertEquals(2, camera.itemCount)
        assertEquals(40L, camera.totalSizeBytes)
        assertEquals("folder:Camera", camera.key)
        assertEquals(0L, groups[2].totalSizeBytes) // cloud-only sizes unknown
    }

    @Test
    fun `months sort newest first with undated items last`() {
        val groups = GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK)

        assertEquals(listOf("April 2024", "March 2024", GroupingEngine.UNKNOWN_DATE), groups.map { it.name })
        assertEquals(listOf("d:3", "d:2", "c:6"), groups[0].items.map { it.key })
        assertEquals(listOf("d:5", "d:1"), groups[1].items.map { it.key })
        assertEquals(listOf("d:4"), groups[2].items.map { it.key })
        assertEquals("month:2024-04", groups[0].key)
    }

    @Test
    fun `group dispatches on mode and handles empty input`() {
        assertEquals(GroupingEngine.byFolder(items), GroupingEngine.group(items, GroupingMode.BY_FOLDER, ZoneOffset.UTC, Locale.UK))
        assertEquals(GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK), GroupingEngine.group(items, GroupingMode.BY_MONTH, ZoneOffset.UTC, Locale.UK))
        assertEquals(emptyList<MediaGroup>(), GroupingEngine.byFolder(emptyList()))
    }
}
