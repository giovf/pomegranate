package com.gankdat.pomegranate.census.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerParserTest {
    @Test
    fun `parses a freshly created session`() {
        val session = PickerParser.session(
            """{"id":"abc","pickerUri":"https://photos.google.com/picker/abc","pollingConfig":{"pollInterval":"2.5s","timeoutIn":"1800s"},"expireTime":"2026-10-20T10:00:00Z","mediaItemsSet":false}""",
        )
        assertEquals("abc", session.id)
        assertEquals("https://photos.google.com/picker/abc", session.pickerUri)
        assertEquals(2_500L, session.pollIntervalMillis)
        assertEquals(1_800_000L, session.timeoutMillis)
        assertEquals(1_792_490_400_000L, session.expireTimeMillis)
        assertFalse(session.mediaItemsSet)
    }

    @Test
    fun `missing polling config falls back to defaults`() {
        val session = PickerParser.session("""{"id":"abc","mediaItemsSet":true}""")
        assertEquals(PickerParser.DEFAULT_POLL_INTERVAL_MILLIS, session.pollIntervalMillis)
        assertEquals(PickerParser.DEFAULT_TIMEOUT_MILLIS, session.timeoutMillis)
        assertNull(session.expireTimeMillis)
        assertTrue(session.mediaItemsSet)
    }

    @Test
    fun `parses a media items page`() {
        val page = PickerParser.mediaItemsPage(
            """{"mediaItems":[
                {"id":"m1","createTime":"2024-03-05T14:30:00.123Z","type":"PHOTO","mediaFile":{"baseUrl":"https://lh3/x","mimeType":"image/jpeg","filename":"IMG_1.jpg","mediaFileMetadata":{"width":4000,"height":3000,"cameraMake":"Google","cameraModel":"Pixel 8","photoMetadata":{"focalLength":4.4}}}},
                {"id":"m2","createTime":"2024-03-06T00:00:00Z","type":"VIDEO","mediaFile":{"baseUrl":"https://lh3/y","mimeType":"video/mp4","filename":"VID_2.mp4","mediaFileMetadata":{"width":1920,"height":1080,"videoMetadata":{"fps":30,"processingStatus":"READY"}}}}
            ],"nextPageToken":"tok"}""",
        )
        assertEquals("tok", page.nextPageToken)
        assertEquals(2, page.items.size)
        val photo = page.items[0]
        assertEquals("m1", photo.id)
        assertEquals(1_709_649_000_123L, photo.createTimeMillis)
        assertEquals("PHOTO", photo.type)
        assertEquals("IMG_1.jpg", photo.filename)
        assertEquals(4_000, photo.width)
        assertEquals("Pixel 8", photo.cameraModel)
        val video = page.items[1]
        assertEquals("VIDEO", video.type)
        assertNull(video.cameraMake)
    }

    @Test
    fun `empty page and missing token`() {
        val page = PickerParser.mediaItemsPage("{}")
        assertTrue(page.items.isEmpty())
        assertNull(page.nextPageToken)
        assertNull(PickerParser.mediaItemsPage("""{"mediaItems":[],"nextPageToken":""}""").nextPageToken)
    }

    @Test
    fun `duration and timestamp helpers`() {
        assertEquals(5_000L, PickerParser.durationMillis("5s"))
        assertEquals(500L, PickerParser.durationMillis("0.5s"))
        assertNull(PickerParser.durationMillis("soon"))
        assertEquals(0L, PickerParser.timestampMillis("1970-01-01T00:00:00Z"))
        assertNull(PickerParser.timestampMillis("yesterday"))
    }
}
