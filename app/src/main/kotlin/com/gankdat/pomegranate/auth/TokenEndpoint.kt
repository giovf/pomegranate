package com.gankdat.pomegranate.auth

import com.gankdat.pomegranate.data.json.Json
import com.gankdat.pomegranate.data.json.JsonException
import com.gankdat.pomegranate.data.json.long
import com.gankdat.pomegranate.data.json.requireString
import com.gankdat.pomegranate.data.json.string
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three calls the app makes to Google's OAuth token service. */
internal interface TokenEndpoint {
    @Throws(IOException::class, TokenEndpointException::class)
    suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse

    @Throws(IOException::class, TokenEndpointException::class)
    suspend fun refresh(refreshToken: String): TokenResponse

    /** Best effort; failures are swallowed because the local wipe happens regardless. */
    suspend fun revoke(token: String)
}

/** Platform `HttpsURLConnection` over TLS as provided by the system; no custom trust. */
internal class HttpTokenEndpoint(
    private val config: OAuthConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TokenEndpoint {
    override suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse = post(
        config.tokenEndpoint,
        mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to config.redirectUri,
            "client_id" to config.clientId,
            "code_verifier" to codeVerifier,
        ),
    )

    override suspend fun refresh(refreshToken: String): TokenResponse = post(
        config.tokenEndpoint,
        mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to config.clientId),
    )

    override suspend fun revoke(token: String) {
        try {
            withContext(ioDispatcher) { send(config.revokeEndpoint, mapOf("token" to token)) }
        } catch (_: IOException) {
            // Offline or Google unreachable: the local wipe still proceeds.
        } catch (_: TokenEndpointException) {
            // Already revoked or unknown token: nothing more to do.
        }
    }

    private suspend fun post(url: String, form: Map<String, String>): TokenResponse = withContext(ioDispatcher) {
        parseTokenResponse(send(url, form))
    }

    private fun send(url: String, form: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(formEncode(form).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw TokenEndpointException(status, oauthErrorCode(body))
            return body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 30_000

        fun formEncode(form: Map<String, String>): String =
            form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }

        fun parseTokenResponse(body: String): TokenResponse {
            val json = Json.parseObject(body)
            return TokenResponse(
                accessToken = json.requireString("access_token"),
                refreshToken = json.string("refresh_token"),
                expiresInSeconds = json.long("expires_in") ?: 0L,
            )
        }

        fun oauthErrorCode(body: String): String? = try {
            Json.parseObject(body).string("error")
        } catch (_: JsonException) {
            null
        }
    }
}
