package com.gankdat.pomegranate.ui.pull

/**
 * Sliding-window throughput and ETA from periodic byte samples; pure. The
 * Picker never reports sizes, so the remaining bytes are estimated from the
 * average size of what has already been pulled.
 */
internal class SpeedEstimator(private val windowSize: Int = 6) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // (timeMillis, cumulativeBytes)

    fun sample(timeMillis: Long, cumulativeBytes: Long) {
        samples.addLast(timeMillis to cumulativeBytes)
        while (samples.size > windowSize) samples.removeFirst()
    }

    fun reset() = samples.clear()

    /** Bytes per second over the window, or null with fewer than two samples or no elapsed time. */
    fun bytesPerSecond(): Double? {
        if (samples.size < 2) return null
        val (t0, b0) = samples.first()
        val (t1, b1) = samples.last()
        if (t1 <= t0) return null
        return (b1 - b0).coerceAtLeast(0) * 1_000.0 / (t1 - t0)
    }

    /** Seconds left, or null while there is no speed or no completed item to size the rest from. */
    fun etaSeconds(itemsRemaining: Int, itemsDone: Int, bytesDone: Long, bytesInFlight: Long): Long? {
        val speed = bytesPerSecond() ?: return null
        if (speed <= 0 || itemsDone == 0) return null
        val averageItem = bytesDone.toDouble() / itemsDone
        val remainingBytes = (itemsRemaining * averageItem - bytesInFlight).coerceAtLeast(0.0)
        return (remainingBytes / speed).toLong()
    }
}
