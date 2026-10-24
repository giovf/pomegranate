package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun device(id: Long, name: String, taken: Long = T0, w: Int = 4000, h: Int = 3000, modified: Long = taken + 1_000) = DeviceItemEntity(
    id = id, mediaStoreId = id, volume = "external_primary", relativePath = "DCIM/Camera/", bucket = "Camera",
    displayName = name, mime = "image/jpeg", size = 1_000, dateTaken = taken, dateModified = modified, width = w, height = h, fingerprint = "fp$id",
)

internal fun cloud(id: Long, name: String, created: Long = T0, w: Int = 4000, h: Int = 3000) = CloudItemEntity(
    id = id, pickerMediaItemId = "g$id", sessionId = "s", filename = name, mime = "image/jpeg", type = "PHOTO",
    createTime = created, width = w, height = h, baseUrl = "https://lh3/$id", baseUrlExpiresAt = 0,
)

internal const val T0 = 1_709_649_000_000L
private const val HOUR = 3_600_000L

class MatchingEngineTest {
    private fun single(cloud: CloudItemEntity, vararg devices: DeviceItemEntity): MatchProposal? =
        MatchingEngine.match(listOf(cloud), devices.toList()).singleOrNull()

    @Test
    fun `same name, time and dimensions is EXACT`() {
        val m = single(cloud(1, "IMG_0001.jpg"), device(10, "IMG_0001.jpg"))!!
        assertEquals(MatchConfidence.EXACT, m.confidence)
        assertEquals(10L, m.deviceItemId)
        assertEquals(TimestampAgreement.EXACT, m.evidence.timestamp)
        assertTrue(m.evidence.filenameMatched && m.evidence.dimensionsMatched)
        assertEquals(MatchMethod.PRIMARY, m.evidence.method)
    }

    @Test
    fun `one second tolerance and case-insensitive names`() {
        val m = single(cloud(1, "img_0001.JPG", created = T0 + 999), device(10, "IMG_0001.jpg"))!!
        assertEquals(MatchConfidence.EXACT, m.confidence)
        assertEquals(999L, m.evidence.timestampDeltaMs)
    }

    @Test
    fun `whole-timezone offsets are LIKELY, not EXACT`() {
        listOf(HOUR, -2 * HOUR, 5 * HOUR + 30 * 60_000, 13 * HOUR).forEach { shift ->
            val m = single(cloud(1, "a.jpg", created = T0 + shift), device(10, "a.jpg"))!!
            assertEquals("shift $shift", MatchConfidence.LIKELY, m.confidence)
            assertEquals(TimestampAgreement.ZONE_SHIFTED, m.evidence.timestamp)
        }
    }

    @Test
    fun `same name and dimensions but different time is AMBIGUOUS`() {
        val m = single(cloud(1, "a.jpg", created = T0 + 7 * 60_000), device(10, "a.jpg"))!!
        assertEquals(MatchConfidence.AMBIGUOUS, m.confidence)
        assertEquals(TimestampAgreement.DIFFERENT, m.evidence.timestamp)
    }

    @Test
    fun `unknown pixel size on the phone does not block a match`() {
        assertEquals(MatchConfidence.LIKELY, single(cloud(1, "PXL_20240305_143000123.jpg"), device(10, "PXL_20240305_143000123.jpg", w = 0, h = 0))!!.confidence)
    }

    @Test
    fun `a filename-derived phone date is compared by day`() {
        val noon = (T0 / 86_400_000L) * 86_400_000L + 12 * HOUR
        val sameDay = single(cloud(1, "IMG-20240305-WA0001.jpg", created = T0), device(10, "IMG-20240305-WA0001.jpg", taken = noon))!!
        assertEquals(MatchConfidence.LIKELY, sameDay.confidence)
        val otherDay = single(cloud(1, "IMG-20240305-WA0001.jpg", created = T0 + 30 * 24 * HOUR), device(10, "IMG-20240305-WA0001.jpg", taken = noon))!!
        assertEquals(MatchConfidence.AMBIGUOUS, otherDay.confidence)
    }

    @Test
    fun `same name in another format of the same kind is at most LIKELY, never across photo and video`() {
        val m = single(cloud(1, "IMG_1234.HEIC").copy(mime = "image/heic"), device(10, "IMG_1234.jpg"))!!
        assertEquals(MatchConfidence.LIKELY, m.confidence)
        assertEquals(false, m.evidence.filenameMatched)
        assertNull(single(cloud(1, "IMG_1234.MOV").copy(mime = "video/quicktime", type = "VIDEO"), device(10, "IMG_1234.jpg")))
    }

    @Test
    fun `same name and time but different dimensions is not a match`() {
        assertNull(single(cloud(1, "a.jpg", w = 1920, h = 1080), device(10, "a.jpg")))
    }

    @Test
    fun `rotated dimensions still count as matching`() {
        val m = single(cloud(1, "a.jpg", w = 3000, h = 4000), device(10, "a.jpg"))!!
        assertEquals(MatchConfidence.EXACT, m.confidence)
    }

    @Test
    fun `unknown timestamp with name and dimensions is AMBIGUOUS`() {
        val m = single(cloud(1, "a.jpg", created = 0), device(10, "a.jpg"))!!
        assertEquals(MatchConfidence.AMBIGUOUS, m.confidence)
        assertEquals(TimestampAgreement.UNKNOWN, m.evidence.timestamp)
    }

    @Test
    fun `a phone date that is only the copy time does not block a match on a distinctive name`() {
        val copied = device(10, "IMG-20190305-WA0001.jpg", taken = T0 + 600 * 24 * HOUR, modified = T0 + 600 * 24 * HOUR)
        val m = single(cloud(1, "IMG-20190305-WA0001.jpg"), copied)!!
        assertEquals(MatchConfidence.LIKELY, m.confidence)
        assertEquals(TimestampAgreement.UNKNOWN, m.evidence.timestamp)
        // A generic name stays uncertain.
        val generic = device(11, "image.jpg", taken = T0 + 600 * 24 * HOUR, modified = T0 + 600 * 24 * HOUR)
        assertEquals(MatchConfidence.AMBIGUOUS, single(cloud(2, "image.jpg"), generic)!!.confidence)
    }

    @Test
    fun `two equally good same-name candidates are AMBIGUOUS`() {
        val m = single(cloud(1, "a.jpg"), device(10, "a.jpg"), device(11, "a.jpg"))!!
        assertEquals(MatchConfidence.AMBIGUOUS, m.confidence)
        assertEquals(2, m.evidence.candidateCount)
    }

    @Test
    fun `the better of two same-name candidates wins`() {
        val m = single(cloud(1, "a.jpg"), device(10, "a.jpg", taken = T0 + 9 * HOUR + 123_000), device(11, "a.jpg"))!!
        assertEquals(11L, m.deviceItemId)
        assertEquals(MatchConfidence.EXACT, m.confidence)
    }

    @Test
    fun `a device item is claimed by at most one cloud item`() {
        val proposals = MatchingEngine.match(listOf(cloud(1, "a.jpg"), cloud(2, "a.jpg")), listOf(device(10, "a.jpg")))
        assertEquals(1, proposals.size)
        assertEquals(1L, proposals.single().cloudItemId)
    }

    @Test
    fun `renamed file with exact time and dimensions is LIKELY via secondary matching`() {
        val m = single(cloud(1, "holiday.jpg"), device(10, "IMG_0001.jpg"))!!
        assertEquals(MatchConfidence.LIKELY, m.confidence)
        assertEquals(MatchMethod.SECONDARY, m.evidence.method)
        assertTrue(!m.evidence.filenameMatched)
    }

    @Test
    fun `renamed file matched only by filename date is AMBIGUOUS`() {
        val m = single(cloud(1, "holiday.jpg", created = T0), device(10, "IMG_20240305_120000.jpg", taken = T0 + 5 * HOUR))!!
        assertEquals(MatchConfidence.AMBIGUOUS, m.confidence)
        assertEquals(MatchMethod.SECONDARY, m.evidence.method)
    }

    @Test
    fun `secondary matching never pairs different dimensions or vague dates`() {
        assertNull(single(cloud(1, "holiday.jpg"), device(10, "IMG_0001.jpg", w = 100, h = 100)))
        assertNull(single(cloud(1, "holiday.jpg"), device(10, "IMG_20231201_120000.jpg", taken = T0 + 5 * HOUR)))
    }

    @Test
    fun `filename dates`() {
        assertEquals(java.time.LocalDate.of(2024, 3, 5).toEpochDay() * 86_400_000, FilenameDates.extract("IMG_20240305_143000.jpg"))
        assertEquals(java.time.LocalDate.of(2024, 3, 5).toEpochDay() * 86_400_000, FilenameDates.extract("Screenshot_2024-03-05-14-30-00.png"))
        assertEquals(java.time.LocalDate.of(2024, 3, 5).toEpochDay() * 86_400_000, FilenameDates.extract("IMG-20240305-WA0001.jpg"))
        assertNull(FilenameDates.extract("IMG_0001.jpg"))
        assertNull(FilenameDates.extract("IMG_20241399_1.jpg"))
    }

    @Test
    fun `evidence survives a JSON round-trip`() {
        val e = Evidence(MatchMethod.SECONDARY, false, 500, TimestampAgreement.EXACT, true, hashMatched = true, candidateCount = 2, note = "n")
        assertEquals(e, Evidence.fromJson(e.toJson()))
        assertNull(Evidence.fromJson("{}"))
        assertNull(Evidence.fromJson("nope"))
    }
}
