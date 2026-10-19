package com.gankdat.pomegranate.census.device

import java.security.MessageDigest

/**
 * Cheap identity for a media file: the fields Google Photos preserves for an
 * uploaded original. Stability contract: the same five inputs always produce
 * the same [hash], across app versions, so stored fingerprints stay valid.
 */
internal data class Fingerprint(
    val displayName: String,
    val size: Long,
    /** 0 when unknown; kept in the hash so "unknown" is consistent, not random. */
    val dateTaken: Long,
    val width: Int,
    val height: Int,
) {
    /** Lowercase hex SHA-256 of the pipe-joined fields. */
    fun hash(): String {
        val input = "$displayName|$size|$dateTaken|$width|$height"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun from(item: ScannedMediaItem): Fingerprint =
            Fingerprint(item.displayName, item.size, item.dateTaken, item.width, item.height)
    }
}
