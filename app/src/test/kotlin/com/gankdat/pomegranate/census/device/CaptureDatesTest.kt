package com.gankdat.pomegranate.census.device

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureDatesTest {
    private val transfer = 1_763_000_000_000L // Nov 2025, the day files were copied to the new phone
    private val march2019 = 1_551_787_200_000L // 2019-03-05
    private val noon5Mar2019 = LocalDate.of(2019, 3, 5).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private var embeddedReads = 0
    private fun embedded(value: Long?): () -> Long? = { embeddedReads++; value }

    @Test
    fun `a capture date clearly older than the file is trusted without reading the file`() {
        assertEquals(march2019, CaptureDates.resolve(march2019, transfer, "holiday.jpg", embedded(1)))
        assertEquals(0, embeddedReads)
    }

    @Test
    fun `a date that matches the filename is trusted without reading the file`() {
        val freshPhoto = 1_700_000_000_000L
        assertEquals(freshPhoto, CaptureDates.resolve(freshPhoto, freshPhoto, "PXL_20231114_221320123.jpg", embedded(1)))
        assertEquals(0, embeddedReads)
    }

    @Test
    fun `copy-time dates are replaced by the file's own metadata`() {
        assertEquals(march2019, CaptureDates.resolve(transfer, transfer, "IMG_0042.HEIC", embedded(march2019)))
        assertEquals(march2019, CaptureDates.resolve(0, transfer, "IMG_0042.HEIC", embedded(march2019)))
    }

    @Test
    fun `without metadata the filename date is used, at midday`() {
        assertEquals(noon5Mar2019, CaptureDates.resolve(transfer, transfer, "IMG-20190305-WA0001.jpg", embedded(null)))
        assertEquals(noon5Mar2019, CaptureDates.resolve(0, transfer, "Screenshot_2019-03-05-10-00-00.png", embedded(null)))
    }

    @Test
    fun `with nothing better the original value is kept`() {
        assertEquals(transfer, CaptureDates.resolve(transfer, transfer, "image.jpg", embedded(null)))
        assertEquals(0L, CaptureDates.resolve(0, transfer, "image.jpg", embedded(null)))
    }

    @Test
    fun `video container dates`() {
        assertEquals(march2019, CaptureDates.parseVideoDate("20190305T120000.000Z"))
        assertEquals(march2019, CaptureDates.parseVideoDate("20190305T120000Z"))
        assertNull(CaptureDates.parseVideoDate("19040101T000000.000Z"))
        assertNull(CaptureDates.parseVideoDate(""))
        assertNull(CaptureDates.parseVideoDate(null))
        assertNull(CaptureDates.parseVideoDate("yesterday"))
    }
}
