package com.gankdat.pomegranate.ui.library

import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.StateIndex
import com.gankdat.pomegranate.reconcile.cloud
import com.gankdat.pomegranate.reconcile.device
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryEntriesTest {
    @Test
    fun `matched pairs appear once as the device file, cloud-only items appear from the cloud index`() {
        val devices = listOf(device(1, "a.jpg"), device(2, "b.jpg"))
        val clouds = listOf(cloud(10, "a.jpg"), cloud(11, "c.jpg").copy(inferredOrigin = "WhatsApp"))
        val states = StateIndex(
            cloudStates = mapOf(10L to MediaState.BOTH, 11L to MediaState.CLOUD_ONLY),
            deviceStates = mapOf(1L to MediaState.BOTH, 2L to MediaState.DEVICE_ONLY),
        )

        val entries = LibraryViewModel.buildEntries(devices, clouds, states)

        assertEquals(listOf("d:1" to MediaState.BOTH, "d:2" to MediaState.DEVICE_ONLY, "c:11" to MediaState.CLOUD_ONLY), entries.map { it.key to it.state })
        assertEquals("WhatsApp", entries.last().bucket)
        assertEquals(ThumbnailSource.Remote("https://lh3/11"), entries.last().thumbnail)
        assertEquals(null, entries.last().sizeBytes)

        val totals = LibraryViewModel.totals(entries)
        assertEquals(StateTotals(1, 1_000), totals[MediaState.BOTH])
        assertEquals(StateTotals(1, 1_000), totals[MediaState.DEVICE_ONLY])
        assertEquals(StateTotals(1, 0), totals[MediaState.CLOUD_ONLY])
    }

    @Test
    fun `before reconciliation everything defaults to its own side`() {
        val entries = LibraryViewModel.buildEntries(listOf(device(1, "a.jpg")), listOf(cloud(10, "a.jpg")), StateIndex.EMPTY)
        assertEquals(listOf(MediaState.DEVICE_ONLY, MediaState.CLOUD_ONLY), entries.map { it.state })
    }
}
