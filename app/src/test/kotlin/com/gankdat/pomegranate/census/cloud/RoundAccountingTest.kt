package com.gankdat.pomegranate.census.cloud

import com.gankdat.pomegranate.census.cloud.RoundAccounting.Round
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundAccountingTest {
    @Test
    fun `two unrecorded rounds are derived from the totals`() {
        // 2,000 + 2,000 selected, 3,714 distinct: the second round re-selected 286.
        val result = RoundAccounting.alreadyIndexed(listOf(Round("r1", 2_000), Round("r2", 2_000)), emptyMap(), totalIndexed = 3_714)
        assertEquals(mapOf("r1" to 0, "r2" to 286), result)
    }

    @Test
    fun `recorded counts are used as they are`() {
        val result = RoundAccounting.alreadyIndexed(listOf(Round("r1", 10), Round("r2", 10), Round("r3", 10)), mapOf("r2" to 3, "r3" to 5), totalIndexed = 22)
        assertEquals(mapOf("r1" to 0, "r2" to 3, "r3" to 5), result)
    }

    @Test
    fun `one unknown among recorded rounds is derived`() {
        val result = RoundAccounting.alreadyIndexed(listOf(Round("r1", 10), Round("r2", 10), Round("r3", 10)), mapOf("r3" to 5), totalIndexed = 22)
        assertEquals(mapOf("r1" to 0, "r2" to 3, "r3" to 5), result)
    }

    @Test
    fun `two unknowns, or inconsistent totals, stay unknown`() {
        val two = RoundAccounting.alreadyIndexed(listOf(Round("r1", 10), Round("r2", 10), Round("r3", 10)), emptyMap(), totalIndexed = 22)
        assertEquals(mapOf("r1" to 0, "r2" to null, "r3" to null), two)
        val inconsistent = RoundAccounting.alreadyIndexed(listOf(Round("r1", 10), Round("r2", 10)), emptyMap(), totalIndexed = 25)
        assertEquals(mapOf("r1" to 0, "r2" to null), inconsistent)
    }
}
