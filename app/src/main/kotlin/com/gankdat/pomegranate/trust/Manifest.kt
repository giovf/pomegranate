package com.gankdat.pomegranate.trust

import com.gankdat.pomegranate.data.db.entity.Fidelity
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.json.Json
import java.io.OutputStream
import java.io.Writer
import java.time.Instant

/** One verified copy, as written to the manifest. */
internal data class ManifestRecord(
    val sourceId: String,
    val originalFilename: String,
    val destinationPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val fidelity: Fidelity,
    val pulledAtMillis: Long,
) {
    /** Always true: only verified jobs become records. Kept as a column so the file is self-explanatory. */
    val verified: Boolean get() = true

    val pulledAtIso: String get() = Instant.ofEpochMilli(pulledAtMillis).toString()
}

internal data class ManifestSummary(val verifiedCount: Int = 0, val totalSizeBytes: Long = 0, val lastPulledAtMillis: Long? = null)

internal object ManifestGenerator {
    /** Only DONE jobs with a hash and a destination qualify; anything else is not proof of anything. */
    fun records(jobs: List<TransferJobEntity>): List<ManifestRecord> = jobs
        .filter { it.state == TransferState.DONE && it.sha256 != null && it.relativePath.isNotEmpty() }
        .sortedWith(compareBy<TransferJobEntity> { it.completedAt ?: 0 }.thenBy { it.id })
        .map {
            ManifestRecord(
                sourceId = it.sourceId,
                originalFilename = it.originalFilename,
                destinationPath = it.relativePath,
                sizeBytes = it.bytesWritten,
                sha256 = checkNotNull(it.sha256),
                fidelity = it.fidelity,
                pulledAtMillis = it.completedAt ?: 0,
            )
        }

    fun summary(records: List<ManifestRecord>): ManifestSummary = ManifestSummary(
        verifiedCount = records.size,
        totalSizeBytes = records.sumOf { it.sizeBytes },
        lastPulledAtMillis = records.maxOfOrNull { it.pulledAtMillis },
    )
}

/** RFC 4180 CSV with a header row; UTF-8. */
internal object CsvExporter {
    val header: List<String> = listOf("source_id", "original_filename", "destination_path", "size_bytes", "sha256_hash", "fidelity", "pulled_at", "verified")

    fun write(records: List<ManifestRecord>, out: OutputStream) {
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.row(header)
            for (r in records) {
                w.row(listOf(r.sourceId, r.originalFilename, r.destinationPath, r.sizeBytes.toString(), r.sha256, r.fidelity.name, r.pulledAtIso, r.verified.toString()))
            }
        }
    }

    private fun Writer.row(fields: List<String>) {
        write(fields.joinToString(",") { escape(it) })
        write("\r\n")
    }

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field
}

/** `{ "metadata": {...}, "records": [...] }`, streamed so large manifests never sit in memory twice. */
internal object JsonExporter {
    fun write(records: List<ManifestRecord>, appVersion: String, exportedAtMillis: Long, out: OutputStream) {
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("{\n  \"metadata\": {")
            w.write("\"app\": ${Json.quote("Pomegranate")}, \"appVersion\": ${Json.quote(appVersion)}, ")
            w.write("\"exportedAt\": ${Json.quote(Instant.ofEpochMilli(exportedAtMillis).toString())}, ")
            w.write("\"totalCount\": ${records.size}, \"totalSizeBytes\": ${records.sumOf { it.sizeBytes }}},\n  \"records\": [")
            records.forEachIndexed { i, r ->
                if (i > 0) w.write(",")
                w.write("\n    {")
                w.write("\"sourceId\": ${Json.quote(r.sourceId)}, \"originalFilename\": ${Json.quote(r.originalFilename)}, ")
                w.write("\"destinationPath\": ${Json.quote(r.destinationPath)}, \"sizeBytes\": ${r.sizeBytes}, ")
                w.write("\"sha256\": ${Json.quote(r.sha256)}, \"fidelity\": ${Json.quote(r.fidelity.name)}, ")
                w.write("\"pulledAt\": ${Json.quote(r.pulledAtIso)}, \"verified\": ${r.verified}}")
            }
            w.write("\n  ]\n}\n")
        }
    }
}
