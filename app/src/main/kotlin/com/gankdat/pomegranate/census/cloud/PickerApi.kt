package com.gankdat.pomegranate.census.cloud

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Picker API calls the app makes. Implementations throw [PickerApiException], [PickerAuthException] or [IOException]. */
internal interface PickerApi {
    suspend fun createSession(): PickerSession

    suspend fun getSession(sessionId: String): PickerSession

    suspend fun listMediaItems(sessionId: String, pageToken: String? = null, pageSize: Int = DEFAULT_PAGE_SIZE): MediaItemsPage

    suspend fun deleteSession(sessionId: String)

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 100
    }
}

/** Platform `HttpsURLConnection` against `photospicker.googleapis.com`; the only non-OAuth endpoint the app contacts. */
internal class HttpPickerApi(
    private val accessToken: suspend () -> String?,
    private val baseUrl: String = BASE_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PickerApi {
    override suspend fun createSession(): PickerSession = PickerParser.session(request("POST", "sessions", body = "{}"))

    override suspend fun getSession(sessionId: String): PickerSession =
        PickerParser.session(request("GET", "sessions/${encode(sessionId)}"))

    override suspend fun listMediaItems(sessionId: String, pageToken: String?, pageSize: Int): MediaItemsPage {
        val query = buildString {
            append("sessionId=").append(encode(sessionId)).append("&pageSize=").append(pageSize)
            if (pageToken != null) append("&pageToken=").append(encode(pageToken))
        }
        return PickerParser.mediaItemsPage(request("GET", "mediaItems?$query"))
    }

    override suspend fun deleteSession(sessionId: String) {
        request("DELETE", "sessions/${encode(sessionId)}")
    }

    private suspend fun request(method: String, path: String, body: String? = null): String {
        val token = accessToken() ?: throw PickerAuthException()
        return withContext(ioDispatcher) {
            val connection = URL(baseUrl + path).openConnection() as HttpsURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val text = (if (status < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw PickerApiException(status, method + " " + path.substringBefore('?'))
                text
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val BASE_URL: String = "https://photospicker.googleapis.com/v1/"
        private const val TIMEOUT_MILLIS = 30_000
    }
}
