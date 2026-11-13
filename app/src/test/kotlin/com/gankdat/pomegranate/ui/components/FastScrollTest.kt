package com.gankdat.pomegranate.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollTest {
    @Test
    fun `fraction runs from the top to the last full screen`() {
        assertEquals(0f, FastScroll.fraction(firstVisible = 0, visibleCount = 10, total = 110), 0f)
        assertEquals(0.5f, FastScroll.fraction(firstVisible = 50, visibleCount = 10, total = 110), 0f)
        assertEquals(1f, FastScroll.fraction(firstVisible = 100, visibleCount = 10, total = 110), 0f)
        assertEquals(0f, FastScroll.fraction(firstVisible = 0, visibleCount = 10, total = 5), 0f)
    }

    @Test
    fun `index covers the whole list and tolerates bad input`() {
        assertEquals(0, FastScroll.index(0f, 200))
        assertEquals(100, FastScroll.index(0.5f, 201))
        assertEquals(199, FastScroll.index(1f, 200))
        assertEquals(199, FastScroll.index(1.7f, 200))
        assertEquals(0, FastScroll.index(0.5f, 0))
    }
}
