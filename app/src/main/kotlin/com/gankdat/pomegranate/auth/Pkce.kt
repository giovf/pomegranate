package com.gankdat.pomegranate.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 helpers. Values must never be logged. */
internal object Pkce {
    const val CHALLENGE_METHOD: String = "S256"
    private const val VERIFIER_BYTES = 32
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 43-character URL-safe verifier from 32 random bytes. */
    fun generateVerifier(): String = encoder.encodeToString(ByteArray(VERIFIER_BYTES).also(random::nextBytes))

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** Opaque anti-CSRF value for the `state` parameter. */
    fun generateState(): String = encoder.encodeToString(ByteArray(16).also(random::nextBytes))
}
