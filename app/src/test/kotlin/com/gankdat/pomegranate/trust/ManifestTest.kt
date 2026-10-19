package com.gankdat.pomegranate.trust

import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.json.Json
import com.gankdat.pomegranate.data.json.long
import com.gankdat.pomegranate.data.json.obj
import com.gankdat.pomegranate.data.json.objects
import com.gankdat.pomegranate.data.json.string
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestTest {
    private fun job(id: Long, state: TransferState = TransferState.DONE, sha: String? = "ab".repeat(32), path: String = "Pomegranate/2024/2024-03/IMG_$id.jpg", done: Long? = 1_709_649_000_000 + id) =
        TransferJobEntity(id = id, cloudItemId = id, sourceId = "g$id", originalFilename = "IMG_$id.jpg", mime = "image/jpeg", state = state, relativePath = path, bytesWritten = 100 * id, sha256 = sha, fidelity = Fidelity.GPS_STRIPPED, completedAt = done)

    @Test
    fun `only verified DONE jobs become records, ordered by completion`() {
        val records = ManifestGenerator.records(listOf(job(3), job(1), job(2, state = TransferState.FAILED), job(4, sha = null), job(5, path = "")))
        assertEquals(listOf("g1", "g3"), records.map { it.sourceId })
        assertEquals(ManifestSummary(verifiedCount = 2, totalSizeBytes = 400, lastPulledAtMillis = 1_709_649_000_003), ManifestGenerator.summary(records))
        assertEquals("2024-03-05T14:30:00.001Z", records[0].pulledAtIso)
    }

    @Test
    fun `csv escapes per RFC 4180`() {
        val record = ManifestRecord("g1", "we, \"quoted\"\nname.jpg", "Pomegranate/x.jpg", 5, "ff", Fidelity.TRANSCODED, 0)
        val out = ByteArrayOutputStream()
        CsvExporter.write(listOf(record), out)
        val lines = out.toString("UTF-8").split("\r\n")
        assertEquals(CsvExporter.header.joinToString(","), lines[0])
        assertEquals("g1,\"we, \"\"quoted\"\"\nname.jpg\",Pomegranate/x.jpg,5,ff,TRANSCODED,1970-01-01T00:00:00Z,true", lines[1])
        assertEquals("", lines[2])
        assertEquals("plain", CsvExporter.escape("plain"))
    }

    @Test
    fun `json export is valid and carries metadata`() {
        val records = listOf(
            ManifestRecord("g1", "a \"b\".jpg", "Pomegranate/a.jpg", 10, "00", Fidelity.GPS_STRIPPED, 1_000),
            ManifestRecord("g2", "b.mp4", "Pomegranate/b.mp4", 20, "11", Fidelity.TRANSCODED, 2_000),
        )
        val out = ByteArrayOutputStream()
        JsonExporter.write(records, appVersion = "0.1.0", exportedAtMillis = 3_000, out = out)

        val root = Json.parseObject(out.toString("UTF-8"))
        val meta = root.obj("metadata")!!
        assertEquals("0.1.0", meta.string("appVersion"))
        assertEquals(2L, meta.long("totalCount"))
        assertEquals(30L, meta.long("totalSizeBytes"))
        assertEquals("1970-01-01T00:00:03Z", meta.string("exportedAt"))
        val items = root.objects("records")
        assertEquals("a \"b\".jpg", items[0].string("originalFilename"))
        assertEquals("TRANSCODED", items[1].string("fidelity"))
        assertEquals(true, items[1]["verified"])
        assertNull(items[0]["missing"])
    }

    @Test
    fun `empty export is still valid JSON`() {
        val out = ByteArrayOutputStream()
        JsonExporter.write(emptyList(), "0.1.0", 0, out)
        assertEquals(0, Json.parseObject(out.toString("UTF-8")).objects("records").size)
    }
}
