package com.gankdat.pomegranate.census.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    private val base = Fingerprint("IMG_0001.jpg", 1_234, 1_700_000_000_000, 4_000, 3_000)

    @Test
    fun `hash is deterministic and pinned`() {
        assertEquals(64, base.hash().length)
        assertEquals(base.hash(), Fingerprint("IMG_0001.jpg", 1_234, 1_700_000_000_000, 4_000, 3_000).hash())
        assertTrue(base.hash().all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `every field participates`() {
        assertNotEquals(base.hash(), base.copy(displayName = "IMG_0002.jpg").hash())
        assertNotEquals(base.hash(), base.copy(size = 1_235).hash())
        assertNotEquals(base.hash(), base.copy(dateTaken = 0).hash())
        assertNotEquals(base.hash(), base.copy(width = 3_000, height = 4_000).hash())
    }

    @Test
    fun `missing date taken is consistent`() {
        assertEquals(base.copy(dateTaken = 0).hash(), base.copy(dateTaken = 0).hash())
    }

    @Test
    fun `factory reads the five identity fields from a scanned item`() {
        val item = scanned(id = 7, displayName = "IMG_0001.jpg", size = 1_234, dateTaken = 1_700_000_000_000)
        assertEquals(base, Fingerprint.from(item))
    }
}

internal fun scanned(
    id: Long,
    volume: String = "external_primary",
    displayName: String = "IMG_$id.jpg",
    bucket: String = "Camera",
    size: Long = 1_000 + id,
    dateTaken: Long = 1_700_000_000_000 + id,
    dateModified: Long = 1_700_000_000_000 + id,
    isVideo: Boolean = false,
) = ScannedMediaItem(
    mediaStoreId = id,
    volume = volume,
    relativePath = "DCIM/$bucket/",
    bucket = bucket,
    displayName = displayName,
    mime = if (isVideo) "video/mp4" else "image/jpeg",
    size = size,
    dateTaken = dateTaken,
    dateModified = dateModified,
    width = 4_000,
    height = 3_000,
    durationMs = if (isVideo) 12_000 else null,
    isVideo = isVideo,
)
