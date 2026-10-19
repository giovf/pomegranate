package com.gankdat.pomegranate.trust

import java.io.InputStream
import java.security.MessageDigest

/** Streaming SHA-256; the same routine hashes device files and every pulled file before it counts as saved. */
internal object Sha256 {
    private const val BUFFER_SIZE = 64 * 1024

    /** Lowercase hex digest of everything readable from [stream]; the caller closes the stream. */
    fun hex(stream: InputStream, onProgress: ((bytesRead: Long) -> Unit)? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            total += read
            onProgress?.invoke(total)
        }
        return digest.digest().toHex()
    }

    fun hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
