package com.gankdat.pomegranate.ui.pull

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedEstimatorTest {
    @Test
    fun `needs two samples and elapsed time`() {
        val e = SpeedEstimator()
        assertNull(e.bytesPerSecond())
        e.sample(0, 0)
        assertNull(e.bytesPerSecond())
        e.sample(0, 100)
        assertNull(e.bytesPerSecond())
        e.sample(2_000, 2_100)
        assertEquals(1_050.0, e.bytesPerSecond()!!, 0.001)
    }

    @Test
    fun `window slides and ETA uses average item size`() {
        val e = SpeedEstimator(windowSize = 3)
        e.sample(0, 0)
        e.sample(1_000, 1_000)
        e.sample(2_000, 2_000)
        e.sample(3_000, 5_000) // drops the first sample: 4,000 bytes over 2 s
        assertEquals(2_000.0, e.bytesPerSecond()!!, 0.001)

        assertNull(e.etaSeconds(itemsRemaining = 10, itemsDone = 0, bytesDone = 0, bytesInFlight = 0))
        // 10 items × 1,000 average, 500 already in flight → 9,500 bytes at 2,000 B/s
        assertEquals(4L, e.etaSeconds(itemsRemaining = 10, itemsDone = 5, bytesDone = 5_000, bytesInFlight = 500))
        e.reset()
        assertNull(e.bytesPerSecond())
    }
}
