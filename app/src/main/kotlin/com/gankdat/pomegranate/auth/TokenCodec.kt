package com.gankdat.pomegranate.auth

import com.gankdat.pomegranate.data.json.Json
import com.gankdat.pomegranate.data.json.JsonException
import com.gankdat.pomegranate.data.json.jsonOf
import com.gankdat.pomegranate.data.json.long
import com.gankdat.pomegranate.data.json.obj
import com.gankdat.pomegranate.data.json.requireString
import com.gankdat.pomegranate.data.json.string

/** Everything the encrypted token file holds. */
internal data class AuthRecord(
    val tokens: StoredTokens? = null,
    val pending: PendingAuthorization? = null,
) {
    val isEmpty: Boolean get() = tokens == null && pending == null
}

/** JSON encoding of [AuthRecord]; the bytes are what gets encrypted. */
internal object TokenCodec {
    fun encode(record: AuthRecord): ByteArray {
        val tokens = record.tokens?.let {
            jsonOf("accessToken" to it.accessToken, "refreshToken" to it.refreshToken, "expiresAtMillis" to it.expiresAtMillis)
        }
        val pending = record.pending?.let { jsonOf("codeVerifier" to it.codeVerifier, "state" to it.state) }
        return "{\"tokens\":${tokens ?: "null"},\"pending\":${pending ?: "null"}}".toByteArray(Charsets.UTF_8)
    }

    /** Returns an empty record for unreadable input rather than throwing; a corrupt file just means "signed out". */
    fun decode(bytes: ByteArray): AuthRecord = try {
        val root = Json.parseObject(String(bytes, Charsets.UTF_8))
        AuthRecord(
            tokens = root.obj("tokens")?.let {
                StoredTokens(
                    accessToken = it.requireString("accessToken"),
                    refreshToken = it.string("refreshToken"),
                    expiresAtMillis = it.long("expiresAtMillis") ?: 0L,
                )
            },
            pending = root.obj("pending")?.let { PendingAuthorization(it.requireString("codeVerifier"), it.requireString("state")) },
        )
    } catch (_: JsonException) {
        AuthRecord()
    }
}
