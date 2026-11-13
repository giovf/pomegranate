package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MatchEntity
import java.util.Locale

/** A verified download: Google's media item id and where the file was written, relative to the destination root. */
internal data class PulledFile(val sourceId: String, val relativePath: String)

/**
 * Pairs each downloaded file with the phone file the census later finds at
 * that path, so a pulled item shows as "on both" without relying on names
 * and dates surviving the download. Pure.
 */
internal object PulledCopies {
    val EVIDENCE: Evidence = Evidence(
        method = MatchMethod.PULLED,
        filenameMatched = true,
        timestampDeltaMs = null,
        timestamp = TimestampAgreement.UNKNOWN,
        dimensionsMatched = false,
        note = "the verified copy Pomegranate downloaded",
    )

    /**
     * The destination root's own path is not known here, so a phone file
     * matches when its path ends with the download's whole relative path
     * (`Pomegranate/2024/2024-03/Camera/IMG_1.jpg`), on a folder boundary.
     */
    fun match(pulled: List<PulledFile>, cloud: List<CloudItemEntity>, device: List<DeviceItemEntity>): List<MatchEntity> {
        if (pulled.isEmpty()) return emptyList()
        val cloudIds = cloud.associate { it.pickerMediaItemId to it.id }
        val byName = device.groupBy { it.displayName.lowercase(Locale.ROOT) }
        val takenDevice = HashSet<Long>()
        val takenCloud = HashSet<Long>()
        val matches = ArrayList<MatchEntity>()
        for (file in pulled) {
            val cloudId = cloudIds[file.sourceId] ?: continue
            if (cloudId in takenCloud) continue
            val wanted = file.relativePath.trim('/').lowercase(Locale.ROOT)
            val candidate = byName[wanted.substringAfterLast('/')].orEmpty().firstOrNull { d ->
                if (d.id in takenDevice) return@firstOrNull false
                val path = (d.relativePath.trim('/') + "/" + d.displayName).trim('/').lowercase(Locale.ROOT)
                path == wanted || path.endsWith("/$wanted")
            } ?: continue
            takenDevice += candidate.id
            takenCloud += cloudId
            matches += MatchEntity(cloudId, candidate.id, MatchConfidence.EXACT, EVIDENCE.toJson())
        }
        return matches
    }
}
