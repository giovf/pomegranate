package com.gankdat.pomegranate.census.device

import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifDatesTest {
    @Test
    fun `parses local time in the given zone`() {
        // 2024-03-05 14:30:00 UTC
        assertEquals(1_709_649_000_000L, ExifDates.toEpochMillis("2024:03:05 14:30:00", zone = ZoneOffset.UTC))
        // Same wall-clock time in Europe/London (GMT in March before DST) is identical.
        assertEquals(1_709_649_000_000L, ExifDates.toEpochMillis("2024:03:05 14:30:00", zone = ZoneId.of("Europe/London")))
    }

    @Test
    fun `an explicit offset wins over the zone`() {
        assertEquals(1_709_649_000_000L - 2 * 3_600_000, ExifDates.toEpochMillis("2024:03:05 14:30:00", offset = "+02:00", zone = ZoneOffset.UTC))
    }

    @Test
    fun `placeholder, blank and malformed values are rejected`() {
        assertNull(ExifDates.toEpochMillis("0000:00:00 00:00:00"))
        assertNull(ExifDates.toEpochMillis(""))
        assertNull(ExifDates.toEpochMillis("   "))
        assertNull(ExifDates.toEpochMillis(null))
        assertNull(ExifDates.toEpochMillis("2024-03-05 14:30:00"))
        assertNull(ExifDates.toEpochMillis("2024:13:05 14:30:00"))
    }

    @Test
    fun `a malformed offset falls back to the zone`() {
        assertEquals(1_709_649_000_000L, ExifDates.toEpochMillis("2024:03:05 14:30:00", offset = "later", zone = ZoneOffset.UTC))
    }
}
