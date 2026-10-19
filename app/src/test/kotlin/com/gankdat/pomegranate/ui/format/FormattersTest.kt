package com.gankdat.pomegranate.ui.format

import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun `file sizes use decimal units with sensible precision`() {
        assertEquals("0 B", Formatters.fileSize(0, Locale.UK))
        assertEquals("999 B", Formatters.fileSize(999, Locale.UK))
        assertEquals("1.0 kB", Formatters.fileSize(1_000, Locale.UK))
        assertEquals("1.5 MB", Formatters.fileSize(1_500_000, Locale.UK))
        assertEquals("120 MB", Formatters.fileSize(120_400_000, Locale.UK))
        assertEquals("2.3 GB", Formatters.fileSize(2_300_000_000, Locale.UK))
        assertEquals("1.0 TB", Formatters.fileSize(1_000_000_000_000, Locale.UK))
    }

    @Test
    fun `durations switch to hours when needed`() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("0:07", Formatters.duration(7_400))
        assertEquals("12:05", Formatters.duration(725_000))
        assertEquals("1:02:03", Formatters.duration(3_723_000))
        assertEquals("0:00", Formatters.duration(-5))
    }

    @Test
    fun `month labels are locale aware`() {
        val march2024 = 1_709_649_000_000L
        assertEquals("March 2024", Formatters.monthYear(march2024, ZoneOffset.UTC, Locale.UK))
        assertEquals("mars 2024", Formatters.monthYear(march2024, ZoneOffset.UTC, Locale.FRANCE))
    }
}
