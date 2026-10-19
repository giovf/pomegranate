package com.gankdat.pomegranate.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class MimeLabelsTest {
    @Test
    fun `known and unknown types`() {
        assertEquals("JPEG image", MimeLabels.label("image/jpeg"))
        assertEquals("JPEG image", MimeLabels.label("IMAGE/JPEG"))
        assertEquals("MP4 video", MimeLabels.label("video/mp4"))
        assertEquals("TIFF image", MimeLabels.label("image/tiff"))
        assertEquals("OGG video", MimeLabels.label("video/ogg"))
        assertEquals("Unknown type", MimeLabels.label(""))
        assertEquals("application/octet-stream", MimeLabels.label("application/octet-stream"))
    }
}
