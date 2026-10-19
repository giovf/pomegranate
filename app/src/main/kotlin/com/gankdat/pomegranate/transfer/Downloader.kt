package com.gankdat.pomegranate.transfer

import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Streams a URL's body into a sink, reporting bytes as they land. */
internal interface Downloader {
    /** Returns the number of bytes written. Throws [TransferException]. */
    fun download(url: String, sink: OutputStream, onProgress: (bytes: Long) -> Unit): Long
}

/** Platform `HttpsURLConnection`; the Picker base URL is a signed URL and needs no auth header. */
internal class HttpDownloader : Downloader {
    override fun download(url: String, sink: OutputStream, onProgress: (bytes: Long) -> Unit): Long {
        val connection = try {
            URL(url).openConnection() as HttpsURLConnection
        } catch (e: IOException) {
            throw TransferException.Network(e.message ?: "connection failed")
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            val status = try {
                connection.responseCode
            } catch (e: IOException) {
                throw TransferException.Network(e.message ?: "no response")
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw if (status == 403 || status == 404 || status == 400) TransferException.SessionGone() else TransferException.Http(status)
            }
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            try {
                connection.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        total += read
                        onProgress(total)
                    }
                    sink.flush()
                }
            } catch (e: IOException) {
                throw TransferException.Network(e.message ?: "download interrupted")
            }
            return total
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
