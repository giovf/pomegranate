package com.gankdat.pomegranate.reconcile.origin

import com.gankdat.pomegranate.data.db.entity.OriginRuleEntity
import com.gankdat.pomegranate.data.db.entity.OriginSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginInferrerTest {
    private val none = OriginInferrer(emptyList())

    @Test
    fun `built-in filename rules`() {
        assertEquals("WhatsApp", none.infer("IMG-20240305-WA0012.jpg")?.origin)
        assertEquals("WhatsApp", none.infer("VID-20240305-WA0003.mp4")?.origin)
        assertEquals("Screenshots", none.infer("Screenshot_2024-03-05-14-30-00.png")?.origin)
        assertEquals("Camera", none.infer("PXL_20240305_143000123.jpg")?.origin)
        assertEquals("Camera", none.infer("IMG_20240305_143000.jpg")?.origin)
        assertEquals("Camera", none.infer("IMG_0042.HEIC")?.origin)
        assertEquals("Camera", none.infer("20240305_143000.jpg")?.origin)
        assertEquals("Telegram", none.infer("IMG_20240305_143000_123.jpg")?.origin)
        assertEquals("Facebook", none.infer("FB_IMG_1709649000000.jpg")?.origin)
        assertEquals("Snapchat", none.infer("Snapchat-123456789.jpg")?.origin)
        assertEquals(BuiltinPatterns.FILENAME_CONFIDENCE, none.infer("PXL_20240305_143000123.jpg")!!.confidence)
        assertEquals(OriginSource.BUILTIN, none.infer("PXL_20240305_143000123.jpg")!!.source)
    }

    @Test
    fun `bucket rules apply when the filename says nothing`() {
        assertEquals("Instagram", none.infer("1709649000000.jpg", bucket = "Instagram")?.origin)
        assertEquals(BuiltinPatterns.BUCKET_CONFIDENCE, none.infer("x.jpg", bucket = "WhatsApp Images")!!.confidence)
        assertNull(none.infer("1709649000000.jpg"))
        assertNull(none.infer("holiday.jpg", bucket = "Holiday 2024"))
    }

    @Test
    fun `confident learned rules beat built-ins, weak ones are ignored`() {
        val strong = OriginRuleEntity(source = OriginSource.LEARNED, pattern = OriginLearner.patternFor("img_"), originLabel = "My camera", confidence = 0.95f)
        val weak = OriginRuleEntity(source = OriginSource.LEARNED, pattern = OriginLearner.patternFor("pxl_"), originLabel = "Somewhere", confidence = 0.6f)

        val inferrer = OriginInferrer(listOf(strong, weak))

        val learned = inferrer.infer("IMG_20240305_143000.jpg")!!
        assertEquals("My camera", learned.origin)
        assertEquals(OriginSource.LEARNED, learned.source)
        assertEquals("Camera", inferrer.infer("PXL_20240305_143000123.jpg")?.origin)
    }

    @Test
    fun `built-in filename evidence beats a less confident learned rule`() {
        val learned = OriginRuleEntity(source = OriginSource.LEARNED, pattern = OriginLearner.patternFor("img-"), originLabel = "Odd folder", confidence = 0.75f)
        assertEquals("WhatsApp", OriginInferrer(listOf(learned)).infer("IMG-20240305-WA0012.jpg")?.origin)
    }

    @Test
    fun `invalid learned patterns are skipped`() {
        val broken = OriginRuleEntity(source = OriginSource.LEARNED, pattern = "([", originLabel = "x", confidence = 1f)
        assertEquals("Camera", OriginInferrer(listOf(broken)).infer("PXL_20240305_143000123.jpg")?.origin)
    }

    @Test
    fun `bucket labels are canonicalised`() {
        assertEquals("WhatsApp", BuiltinPatterns.labelForBucket("WhatsApp Images"))
        assertEquals("Camera", BuiltinPatterns.labelForBucket("Camera"))
        assertEquals("Holiday", BuiltinPatterns.labelForBucket("HOLIDAY"))
    }
}
