package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.model.MediaState
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

internal fun entry(id: Long, bucket: String?, dateTaken: Long, size: Long? = 100, cloud: Boolean = false, volume: String = "external_primary") = LibraryEntry(
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
    thumbnail = if (cloud) ThumbnailSource.Remote("https://lh3/$id", id, "s") else ThumbnailSource.Local("external_primary", id),
    volume = if (cloud) null else volume,
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
        entry(7, "Camera", april, size = null, cloud = true),
    )

    @Test
    fun `phone folders first, then Google-only groups, never merged by name`() {
        val groups = GroupingEngine.byFolder(items)

        assertEquals(
            listOf("Camera", "camera2", "Screenshots", GroupingEngine.UNKNOWN_FOLDER, GroupingEngine.CLOUD_FOLDER_PREFIX + "Camera", GroupingEngine.CLOUD_UNKNOWN_FOLDER),
            groups.map { it.name },
        )
        val camera = groups.first()
        assertEquals(listOf("d:3", "d:1"), camera.items.map { it.key })
        assertEquals(2, camera.itemCount)
        assertEquals(40L, camera.totalSizeBytes)
        assertEquals("folder:Camera", camera.key)
        assertEquals(listOf("d:3", "d:1"), groups.first().items.map { it.key }) // Google-only "Camera" items are not in the phone folder
        assertEquals(true, groups[4].isCloudOnly)
        assertEquals(0L, groups[5].totalSizeBytes) // Google-only sizes unknown
        assertEquals(emptyList<Pair<String, Long>>(), groups[5].sizeByVolume)
    }

    @Test
    fun `months sort newest first with undated items last`() {
        val groups = GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK)

        assertEquals(listOf("April 2024", "March 2024", GroupingEngine.UNKNOWN_DATE), groups.map { it.name })
        assertEquals(listOf("d:3", "d:2", "c:7", "c:6"), groups[0].items.map { it.key })
        assertEquals(listOf("d:5", "d:1"), groups[1].items.map { it.key })
        assertEquals(listOf("d:4"), groups[2].items.map { it.key })
        assertEquals("month:2024-04", groups[0].key)
    }

    @Test
    fun `size is split per storage volume, largest first`() {
        val group = GroupingEngine.byFolder(listOf(entry(1, "DCIM", march, size = 10), entry(2, "DCIM", march, size = 30, volume = "1234-abcd"), entry(3, "DCIM", march, size = 5))).single()
        assertEquals(listOf("1234-abcd" to 30L, "external_primary" to 15L), group.sizeByVolume)
    }

    @Test
    fun `group dispatches on mode and handles empty input`() {
        assertEquals(GroupingEngine.byFolder(items), GroupingEngine.group(items, GroupingMode.BY_FOLDER, ZoneOffset.UTC, Locale.UK))
        assertEquals(GroupingEngine.byMonth(items, ZoneOffset.UTC, Locale.UK), GroupingEngine.group(items, GroupingMode.BY_MONTH, ZoneOffset.UTC, Locale.UK))
        assertEquals(emptyList<MediaGroup>(), GroupingEngine.byFolder(emptyList()))
    }

    @Test
    fun `day sections follow item order and label each calendar day once`() {
        val day = 86_400_000L
        val sorted = listOf(entry(1, "Camera", april + 60_000), entry(2, "Camera", april), entry(3, "Camera", april - day), entry(4, "Camera", march), entry(5, "Camera", 0), entry(6, "Camera", 0))

        val sections = GroupingEngine.byDay(sorted, ZoneOffset.UTC, Locale.UK)

        assertEquals(listOf("Thu 4 Apr 2024", "Wed 3 Apr 2024", "Tue 5 Mar 2024", GroupingEngine.UNKNOWN_DATE), sections.map { it.label })
        assertEquals(listOf(listOf(1L, 2L), listOf(3L), listOf(4L), listOf(5L, 6L)), sections.map { s -> s.items.map { it.deviceItemId } })
        assertEquals(sections.size, sections.map { it.key }.toSet().size)
    }

    @Test
    fun `day sections of an empty group are empty`() {
        assertEquals(emptyList<DaySection>(), GroupingEngine.byDay(emptyList(), ZoneOffset.UTC, Locale.UK))
    }
}
