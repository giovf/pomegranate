package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulledCopiesTest {
    private fun pulledDevice(id: Long, folder: String, name: String) = device(id, name).copy(relativePath = folder)

    @Test
    fun `a download pairs with the phone file at its path under any root`() {
        val clouds = listOf(cloud(1, "IMG_1.jpg"), cloud(2, "IMG_2.jpg"))
        val devices = listOf(
            pulledDevice(10, "Pictures/Pomegranate/2024/2024-03/Camera/", "IMG_1.jpg"),
            pulledDevice(11, "Pomegranate/2024/2024-03/", "IMG_2.jpg"),
        )
        val pulled = listOf(PulledFile("g1", "Pomegranate/2024/2024-03/Camera/IMG_1.jpg"), PulledFile("g2", "Pomegranate/2024/2024-03/IMG_2.jpg"))

        val matches = PulledCopies.match(pulled, clouds, devices)

        assertEquals(listOf(1L to 10L, 2L to 11L), matches.map { it.cloudItemId to it.deviceItemId })
        assertTrue(matches.all { it.confidence == MatchConfidence.EXACT && Evidence.fromJson(it.evidence)?.method == MatchMethod.PULLED })
    }

    @Test
    fun `same name elsewhere, a partial folder name or an unknown item is not a pair`() {
        val clouds = listOf(cloud(1, "IMG_1.jpg"))
        val pulled = listOf(PulledFile("g1", "Pomegranate/2024/2024-03/IMG_1.jpg"), PulledFile("gone", "Pomegranate/2024/2024-03/IMG_9.jpg"))

        assertEquals(0, PulledCopies.match(pulled, clouds, listOf(pulledDevice(10, "DCIM/Camera/", "IMG_1.jpg"))).size)
        assertEquals(0, PulledCopies.match(pulled, clouds, listOf(pulledDevice(10, "NotPomegranate/2024/2024-03/", "IMG_1.jpg"))).size)
        assertEquals(0, PulledCopies.match(pulled, clouds, listOf(pulledDevice(10, "Pomegranate/2024/2024-03/", "IMG_9.jpg"))).size)
    }

    @Test
    fun `each phone file and each item is used once`() {
        val clouds = listOf(cloud(1, "IMG_1.jpg"))
        val devices = listOf(pulledDevice(10, "Pomegranate/2024/2024-03/", "IMG_1.jpg"), pulledDevice(11, "Pictures/Pomegranate/2024/2024-03/", "IMG_1.jpg"))
        val pulled = listOf(PulledFile("g1", "Pomegranate/2024/2024-03/IMG_1.jpg"), PulledFile("g1", "Pomegranate/2024/2024-03/IMG_1.jpg"))

        assertEquals(1, PulledCopies.match(pulled, clouds, devices).size)
    }
}
