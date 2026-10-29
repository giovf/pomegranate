package com.gankdat.pomegranate.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic JPEGs: real marker structure, fake image data. */
internal object Jpegs {
    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    val jfif = segment(0xE0, "JFIF\u0000\u0001\u0001".toByteArray(Charsets.ISO_8859_1))
    val exif = segment(0xE1, "Exif\u0000\u0000MM\u0000*DATE=2024".toByteArray(Charsets.ISO_8859_1))
    val quant = segment(0xDB, ByteArray(65) { it.toByte() })
    fun xmp(body: String) = segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000" + body).toByteArray(Charsets.UTF_8))

    /** SOS header + entropy data containing a stuffed FF 00 and a restart marker, then EOI. */
    val scan = segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)) + byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD0.toByte(), 0x56, 0xFF.toByte(), 0xD9.toByte())

    fun build(vararg segments: ByteArray, trailer: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + segments.fold(ByteArray(0)) { a, b -> a + b } + scan + trailer

    fun mp4(size: Int, brand: String = "isom"): ByteArray =
        byteArrayOf(0, 0, 0, 24) + "ftyp$brand".toByteArray(Charsets.ISO_8859_1) + ByteArray(size - 12) { (it % 251).toByte() }
}

class MotionPhotosTest {
    @Test
    fun `motion photo file names are recognised and ordinary ones are not`() {
        assertTrue(MotionPhotos.nameSuggestsMotion("PXL_20240305_101112345.MP.jpg"))
        assertTrue(MotionPhotos.nameSuggestsMotion("pxl_20240305_101112345.mp.JPEG"))
        assertTrue(MotionPhotos.nameSuggestsMotion("MVIMG_20190101_120000.jpg"))
        assertTrue(MotionPhotos.nameSuggestsMotion("PXL_20240305_101112345.MP.heic"))
        assertFalse(MotionPhotos.nameSuggestsMotion("PXL_20240305_101112345.jpg"))
        assertFalse(MotionPhotos.nameSuggestsMotion("IMG_20240305_101112.jpg"))
        assertFalse(MotionPhotos.nameSuggestsMotion("MP_trip.jpg"))
    }

    @Test
    fun `xmp motion markers are recognised in attribute and element form`() {
        assertTrue(MotionPhotos.xmpSaysMotion(Jpegs.build(Jpegs.xmp("""<rdf:Description GCamera:MotionPhoto="1"/>"""))))
        assertTrue(MotionPhotos.xmpSaysMotion(Jpegs.build(Jpegs.xmp("""<rdf:Description Camera:MotionPhoto='1'/>"""))))
        assertTrue(MotionPhotos.xmpSaysMotion(Jpegs.build(Jpegs.xmp("""<GCamera:MicroVideo>1</GCamera:MicroVideo>"""))))
        assertFalse(MotionPhotos.xmpSaysMotion(Jpegs.build(Jpegs.xmp("""<rdf:Description GCamera:MotionPhoto="0"/>"""))))
        assertFalse(MotionPhotos.xmpSaysMotion(Jpegs.build(Jpegs.exif)))
    }

    @Test
    fun `plan sends ordinary photos straight through and only motion JPEGs fetch a clip`() {
        val plain = Jpegs.build(Jpegs.exif)
        assertEquals(MotionPlan.NOT_MOTION, MotionPhotos.plan("IMG_1.jpg", plain))
        assertEquals(MotionPlan.FETCH_CLIP, MotionPhotos.plan("PXL_1.MP.jpg", plain))
        assertEquals(MotionPlan.FETCH_CLIP, MotionPhotos.plan("20240305_101112.jpg", Jpegs.build(Jpegs.xmp("""GCamera:MotionPhoto="1""""))))
        val heic = byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray(Charsets.ISO_8859_1) + ByteArray(20)
        assertEquals(MotionPlan.STILL_ONLY, MotionPhotos.plan("PXL_1.MP.heic", heic))
    }

    @Test
    fun `clip type is read from the ftyp box`() {
        assertTrue(MotionPhotos.isMp4(Jpegs.mp4(40)))
        assertEquals("video/mp4", MotionPhotos.clipMime(Jpegs.mp4(40)))
        assertEquals("video/quicktime", MotionPhotos.clipMime(Jpegs.mp4(40, brand = "qt  ")))
        assertFalse(MotionPhotos.isMp4(Jpegs.build(Jpegs.exif)))
        assertFalse(MotionPhotos.isMp4(ByteArray(5)))
    }

    @Test
    fun `rebuilt file keeps the still, declares the clip and ends with it`() {
        val still = Jpegs.build(Jpegs.jfif, Jpegs.exif, Jpegs.quant)
        val clip = Jpegs.mp4(5_000)
        val out = rebuild(still, clip)

        // Still part: SOI, JFIF, EXIF, then the new XMP, then the remaining segments and image data unchanged.
        val stillPart = out.copyOfRange(0, out.size - clip.size)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + Jpegs.jfif + Jpegs.exif, stillPart.copyOfRange(0, 2 + Jpegs.jfif.size + Jpegs.exif.size))
        val xmpStart = 2 + Jpegs.jfif.size + Jpegs.exif.size
        assertEquals(0xE1, stillPart[xmpStart + 1].toInt() and 0xFF)
        val tail = Jpegs.quant + Jpegs.scan
        assertArrayEquals(tail, stillPart.copyOfRange(stillPart.size - tail.size, stillPart.size))
        assertArrayEquals(clip, out.copyOfRange(out.size - clip.size, out.size))

        val text = String(out, Charsets.ISO_8859_1)
        assertTrue("""Item:Semantic="MotionPhoto" Item:Length="5000"""" in text)
        assertTrue("""GCamera:MicroVideoOffset="5000"""" in text)
        assertEquals(5_000L, MotionPhotos.embeddedVideoLength(out))
        assertEquals(MotionPlan.ALREADY_MOTION, MotionPhotos.plan("PXL_1.MP.jpg", out))
    }

    @Test
    fun `old xmp and trailing bytes are replaced, so rebuilding twice gives the same file`() {
        val stale = Jpegs.xmp("""GCamera:MotionPhoto="1" Item:Length="99"""")
        val still = Jpegs.build(Jpegs.exif, stale, Jpegs.quant, trailer = ByteArray(99) { 7 })
        val clip = Jpegs.mp4(300, brand = "qt  ")
        val once = rebuild(still, clip)
        assertEquals(1, Regex("http://ns.adobe.com/xap/1.0/").findAll(String(once, Charsets.ISO_8859_1)).count())
        assertTrue("""Item:Mime="video/quicktime"""" in String(once, Charsets.ISO_8859_1))
        assertArrayEquals(once, rebuild(once.copyOfRange(0, once.size - clip.size), clip))
    }

    @Test
    fun `a still with no app segments gets the xmp right after SOI`() {
        val out = rebuild(Jpegs.build(Jpegs.quant), Jpegs.mp4(64))
        assertEquals(0xE1, out[3].toInt() and 0xFF)
        assertEquals(64L, MotionPhotos.embeddedVideoLength(out))
    }

    @Test
    fun `xmp pointing at a clip that is not there is ignored`() {
        val lying = Jpegs.build(Jpegs.xmp("""<Container:Item Item:Semantic="MotionPhoto" Item:Length="40"/>"""))
        assertNull(MotionPhotos.embeddedVideoLength(lying))
        assertEquals(MotionPlan.FETCH_CLIP, MotionPhotos.plan("PXL_1.MP.jpg", lying))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed jpeg is rejected`() {
        rebuild(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01, 0x02), Jpegs.mp4(40))
    }

    private fun rebuild(still: ByteArray, clip: ByteArray): ByteArray {
        val sink = ByteArrayOutputStream()
        val written = MotionPhotoWriter.write(still, ByteArrayInputStream(clip), clip.size.toLong(), MotionPhotos.clipMime(clip), sink)
        assertEquals(sink.size().toLong(), written)
        return sink.toByteArray()
    }
}
