package com.gankdat.pomegranate.ui.connection

import com.gankdat.pomegranate.reconcile.T0
import com.gankdat.pomegranate.reconcile.cloud
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundPendingTest {
    private val day = 86_400_000L

    @Test
    fun `only items that are neither on the phone nor downloaded are owed, with their dates`() {
        val items = listOf(
            cloud(1, "a.jpg", created = T0), // 2024-03-05
            cloud(2, "b.jpg", created = T0 + 40 * day), // 2024-04-14
            cloud(3, "c.jpg", created = T0 + 41 * day),
            cloud(4, "on-phone.jpg", created = T0 - 400 * day),
            cloud(5, "pulled.jpg", created = T0 + 400 * day),
            cloud(6, "undated.jpg", created = 0),
            cloud(7, "other-round.jpg").copy(sessionId = "s2"),
        )

        val pending = RoundPending.bySession(items, onPhone = setOf(4L), pulled = setOf("g5"), zone = ZoneOffset.UTC)

        val s = pending.getValue("s")
        assertEquals(setOf(1L, 2L, 3L, 6L), s.cloudItemIds)
        assertEquals(T0, s.oldest)
        assertEquals(T0 + 41 * day, s.newest)
        assertEquals(listOf(YearMonth.of(2024, 4) to 2, YearMonth.of(2024, 3) to 1), s.months)
        assertEquals(1, pending.getValue("s2").count)
    }

    @Test
    fun `a round with nothing owed is absent`() {
        assertNull(RoundPending.bySession(listOf(cloud(1, "a.jpg")), onPhone = setOf(1L), pulled = emptySet())["s"])
    }
}
