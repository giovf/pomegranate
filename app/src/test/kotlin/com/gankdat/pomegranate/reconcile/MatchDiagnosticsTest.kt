package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchDiagnosticsTest {
    @Test
    fun `unmatched Google items are explained by the closest kind of phone candidate`() {
        val devices = listOf(
            device(1, "a.jpg"),
            device(2, "b.jpg", taken = T0 + 99_000_000),
            device(3, "c.jpg"),
            device(4, "renamed.jpg", taken = T0 + 30_000),
            device(5, "x.jpg").copy(bucket = "WhatsApp Images"),
            device(6, "y.jpg").copy(bucket = "WhatsApp Images"),
        )
        val clouds = listOf(
            cloud(11, "a.jpg"), // matched
            cloud(12, "b.jpg"), // same name, different date
            cloud(13, "C.HEIC"), // other format
            cloud(14, "holiday.jpg"), // renamed: same size, 30 s apart
            cloud(15, "only.mp4", w = 1920, h = 1080), // nothing similar
            cloud(16, "x.jpg"), // uncertain pair
        )
        val matches = listOf(MatchEntity(11, 1, MatchConfidence.EXACT, "{}"), MatchEntity(16, 5, MatchConfidence.AMBIGUOUS, "{}"))

        val d = MatchDiagnostics.analyze(devices, clouds, matches)

        assertEquals(5, d.cloudNotOnPhone)
        assertEquals(2, d.sameNameDifferentDetails) // b.jpg and the uncertain x.jpg
        assertEquals(1, d.sameNameOtherFormat)
        assertEquals(1, d.renamedCandidates)
        assertEquals(1, d.noCandidate)
        assertEquals(1, d.uncertain)
        assertEquals(listOf("Camera" to 3, "WhatsApp Images" to 2), d.phoneOnlyFolders)
        assertEquals(listOf("JPG" to 3, "HEIC" to 1, "MP4" to 1), d.cloudOnlyTypes)
    }
}
