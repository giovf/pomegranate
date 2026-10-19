package com.gankdat.pomegranate.transfer

import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.model.FolderStrategy
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PureTransferLogicTest {
    private val march5 = 1_709_649_000_000L

    @Test
    fun `folder layout by origin and by month`() {
        assertEquals(listOf("Pomegranate", "2024", "2024-03", "WhatsApp"), DestinationLayout.folders(march5, "WhatsApp", FolderStrategy.BY_ORIGIN, ZoneOffset.UTC))
        assertEquals(listOf("Pomegranate", "2024", "2024-03"), DestinationLayout.folders(march5, null, FolderStrategy.BY_ORIGIN, ZoneOffset.UTC))
        assertEquals(listOf("Pomegranate", "2024", "2024-03"), DestinationLayout.folders(march5, "WhatsApp", FolderStrategy.BY_MONTH_ONLY, ZoneOffset.UTC))
        assertEquals(listOf("Pomegranate", "1970", "1970-01"), DestinationLayout.folders(0, null, FolderStrategy.BY_ORIGIN, ZoneOffset.UTC))
        assertEquals(listOf("Pomegranate", "2024", "2024-03", "A_B"), DestinationLayout.folders(march5, "A/B", FolderStrategy.BY_ORIGIN, ZoneOffset.UTC))
    }

    @Test
    fun `unique names add a counter and strip unsafe characters`() {
        val taken = setOf("IMG_1.jpg", "IMG_1 (1).jpg")
        assertEquals("IMG_1 (2).jpg", DestinationLayout.uniqueName("IMG_1.jpg") { it in taken })
        assertEquals("IMG_2.jpg", DestinationLayout.uniqueName("IMG_2.jpg") { it in taken })
        assertEquals("a_b_c.jpg", DestinationLayout.uniqueName("a/b:c.jpg") { false })
        assertEquals("noext (1)", DestinationLayout.uniqueName("noext") { it == "noext" })
        assertEquals("file", DestinationLayout.uniqueName("...") { false })
    }

    @Test
    fun `retry policy`() {
        assertEquals(TransferState.QUEUED, RetryPolicy.nextState(TransferException.Network("x"), attemptsSoFar = 1))
        assertEquals(TransferState.QUEUED, RetryPolicy.nextState(TransferException.Http(503), attemptsSoFar = 4))
        assertEquals(TransferState.FAILED, RetryPolicy.nextState(TransferException.Network("x"), attemptsSoFar = RetryPolicy.MAX_ATTEMPTS))
        assertEquals(TransferState.FAILED, RetryPolicy.nextState(TransferException.SessionGone(), attemptsSoFar = 1))
        assertEquals(TransferState.FAILED, RetryPolicy.nextState(TransferException.Storage("full"), attemptsSoFar = 1))
        assertEquals(TransferState.QUEUED, RetryPolicy.nextState(TransferException.Verification("bad"), attemptsSoFar = 1))
        assertEquals(TransferState.FAILED, RetryPolicy.nextState(TransferException.Http(404), attemptsSoFar = 1))
    }

    @Test
    fun `fidelity and download suffix follow the Picker's limits`() {
        assertEquals(Fidelity.GPS_STRIPPED, FidelityClassifier.forPicker(isVideo = false))
        assertEquals(Fidelity.TRANSCODED, FidelityClassifier.forPicker(isVideo = true))
        assertEquals("=d", FidelityClassifier.downloadSuffix(false))
        assertEquals("=dv", FidelityClassifier.downloadSuffix(true))
    }

    @Test
    fun `document ids map to scannable paths only for external storage`() {
        assertEquals("/storage/emulated/0/Pomegranate/2024/x.jpg", SafPaths.toFilePath("primary:Pomegranate/2024/x.jpg"))
        assertEquals("/storage/emulated/0", SafPaths.toFilePath("primary:"))
        assertEquals("/storage/1234-ABCD/Photos", SafPaths.toFilePath("1234-ABCD:Photos"))
        assertNull(SafPaths.toFilePath("home:Pictures"))
        assertNull(SafPaths.toFilePath("weird"))
    }

    @Test
    fun `summary counts states and bytes`() {
        val s = TransferSummary.of(
            listOf(
                TransferState.QUEUED to (0L to 0L),
                TransferState.RUNNING to (10L to 100L),
                TransferState.DONE to (50L to 50L),
                TransferState.FAILED to (0L to 0L),
                TransferState.PAUSED to (0L to 0L),
                TransferState.VERIFYING to (20L to 20L),
            ),
        )
        assertEquals(TransferSummary(queued = 1, running = 1, verifying = 1, done = 1, failed = 1, paused = 1, bytesWritten = 80, bytesExpected = 170), s)
        assertEquals(6, s.total)
        assertEquals(true, s.isActive)
    }
}
