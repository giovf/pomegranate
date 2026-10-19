package com.gankdat.pomegranate.reconcile.origin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginLearnerTest {
    private fun files(bucket: String, prefix: String, n: Int) = (1..n).map { CensusFile(bucket, "$prefix${20240300 + it}_120000.jpg") }

    @Test
    fun `prefix extraction`() {
        assertEquals("img_", OriginLearner.prefixOf("IMG_20240305_143000.jpg"))
        assertEquals("img-", OriginLearner.prefixOf("IMG-20240305-WA0001.jpg"))
        assertEquals("screenshot_", OriginLearner.prefixOf("Screenshot_2024-03-05.png"))
        assertNull(OriginLearner.prefixOf("1709649000000.jpg"))
        assertNull(OriginLearner.prefixOf("holiday.jpg"))
        assertNull(OriginLearner.prefixOf("a1.jpg")) // one letter is not a convention
    }

    @Test
    fun `rules need support and exclusivity to a folder`() {
        val census = files("Camera", "IMG_", 20) + files("Camera", "VID_", 6) +
            files("WhatsApp Images", "IMG-", 30) + files("Camera", "IMG-", 2) +
            files("Misc", "DSC_", 3)

        val rules = OriginLearner.learn(census)

        val byPattern = rules.associateBy { it.pattern }
        assertEquals("Camera", byPattern[OriginLearner.patternFor("img_")]?.originLabel)
        assertEquals(1f, byPattern[OriginLearner.patternFor("img_")]!!.confidence)
        assertEquals("Camera", byPattern[OriginLearner.patternFor("vid_")]?.originLabel) // 6 files, exclusive
        assertEquals("WhatsApp", byPattern[OriginLearner.patternFor("img-")]?.originLabel)
        assertEquals(30f / 32f, byPattern[OriginLearner.patternFor("img-")]!!.confidence, 0.001f)
        assertNull(byPattern[OriginLearner.patternFor("dsc_")]) // below MIN_SUPPORT
        assertEquals(3, rules.size)
        assertTrue(rules.zipWithNext().all { (a, b) -> a.confidence >= b.confidence })
    }

    @Test
    fun `a prefix split evenly across folders is not learned`() {
        val rules = OriginLearner.learn(files("A", "IMG_", 10) + files("B", "IMG_", 10) + files("C", "IMG_", 10))
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `learned patterns match case-insensitively and anchor at the start`() {
        val regex = Regex(OriginLearner.patternFor("img_"))
        assertTrue(regex.containsMatchIn("img_1.jpg"))
        assertTrue(regex.containsMatchIn("IMG_20240305_1.jpg"))
        assertTrue(!regex.containsMatchIn("MVIMG_20240305_1.jpg"))
    }
}
