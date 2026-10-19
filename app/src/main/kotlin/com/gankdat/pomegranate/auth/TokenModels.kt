package com.gankdat.pomegranate.auth

/** What the app keeps between sessions; encrypted at rest by [TokenStore]. */
internal data class StoredTokens(
    val accessToken: String,
    val refreshToken: String?,
    /** Epoch millis after which [accessToken] is treated as expired (includes a safety buffer). */
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

/** A sign-in that has been launched but not yet completed; needed to finish the PKCE exchange. */
internal data class PendingAuthorization(
    val codeVerifier: String,
    val state: String,
)

/** Parsed token endpoint response. */
internal data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
)

internal sealed interface AuthResult {
    data object Success : AuthResult

    /** The user cancelled or Google denied consent. */
    data object Cancelled : AuthResult

    /** The refresh token is gone or rejected (for example the 7-day Testing-mode expiry); sign in again. */
    data object NeedsReauth : AuthResult

    /** Non-identifying description of what failed. */
    data class Error(val message: String) : AuthResult
}

/** What the connection screen shows. */
internal sealed interface AuthState {
    /** No client id in the build; connecting is impossible. */
    data object NotConfigured : AuthState

    data object SignedOut : AuthState

    /** Browser handed off; waiting for the redirect or exchanging the code. */
    data object SigningIn : AuthState

    data object SignedIn : AuthState

    data object NeedsReauth : AuthState

    data class Error(val message: String) : AuthState
}

/** Raised by [TokenEndpoint] for HTTP-level failures. */
internal class TokenEndpointException(
    val httpStatus: Int,
    /** OAuth `error` code such as `invalid_grant`, if the body had one. */
    val oauthError: String?,
) : Exception("token endpoint returned $httpStatus${oauthError?.let { " ($it)" } ?: ""}")
