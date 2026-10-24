package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.model.MediaState
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageCalculatorTest {
    private val march = 1_709_649_000_000L // 2024-03-05
    private val april = 1_712_241_000_000L // 2024-04-04
    private val may = 1_715_000_000_000L // 2024-05-06
    private val june = 1_718_000_000_000L // 2024-06-10

    @Test
    fun `months are compared side by side with a hint each, newest first and undated last`() {
        val devices = listOf(
            device(1, "a.jpg", taken = march), device(2, "b.jpg", taken = march), // March: both matched
            device(3, "c.jpg", taken = april), device(4, "d.jpg", taken = april), // April: one matched
            device(5, "e.jpg", taken = may), // May: never indexed
            device(6, "f.jpg", taken = 0), // undated
        )
        val clouds = listOf(
            cloud(11, "a.jpg", created = march), cloud(12, "b.jpg", created = march),
            cloud(13, "c.jpg", created = april), cloud(14, "x.jpg", created = april),
            cloud(15, "y.jpg", created = june), // June: cloud only
        )
        val states = StateIndex(
            cloudStates = mapOf(11L to MediaState.BOTH, 12L to MediaState.BOTH, 13L to MediaState.BOTH, 14L to MediaState.CLOUD_ONLY, 15L to MediaState.CLOUD_ONLY),
            deviceStates = mapOf(1L to MediaState.BOTH, 2L to MediaState.BOTH, 3L to MediaState.BOTH, 4L to MediaState.DEVICE_ONLY, 5L to MediaState.DEVICE_ONLY, 6L to MediaState.DEVICE_ONLY),
        )

        val rows = CoverageCalculator.byMonth(devices, clouds, states, ZoneOffset.UTC)

        assertEquals(listOf(YearMonth.of(2024, 6), YearMonth.of(2024, 5), YearMonth.of(2024, 4), YearMonth.of(2024, 3), null), rows.map { it.month })
        assertEquals(listOf(CoverageHint.CLOUD_ONLY, CoverageHint.NOT_INDEXED, CoverageHint.PARTIAL, CoverageHint.COVERED, CoverageHint.NOT_INDEXED), rows.map { it.hint })
        assertEquals(MonthCoverage(YearMonth.of(2024, 4), deviceCount = 2, deviceMatched = 1, cloudCount = 2, cloudOnly = 1), rows[2])
        assertEquals(1, rows[0].cloudOnly)
    }

    @Test
    fun `empty indexes give no rows and unknown states count as unmatched`() {
        assertEquals(emptyList<MonthCoverage>(), CoverageCalculator.byMonth(emptyList(), emptyList(), StateIndex.EMPTY))
        val rows = CoverageCalculator.byMonth(listOf(device(1, "a.jpg", taken = march)), listOf(cloud(2, "a.jpg", created = march)), StateIndex.EMPTY, ZoneOffset.UTC)
        assertEquals(CoverageHint.PARTIAL, rows.single().hint)
        assertEquals(1, rows.single().cloudOnly)
    }
}
