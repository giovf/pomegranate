package com.gankdat.pomegranate.auth

import com.gankdat.pomegranate.BuildConfig

/** Google OAuth endpoints and this app's public client configuration. */
internal data class OAuthConfig(
    val clientId: String = BuildConfig.OAUTH_CLIENT_ID,
    val redirectUri: String = REDIRECT_URI,
    val scope: String = SCOPE,
    val authEndpoint: String = AUTH_ENDPOINT,
    val tokenEndpoint: String = TOKEN_ENDPOINT,
    val revokeEndpoint: String = REVOKE_ENDPOINT,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank()

    companion object {
        /**
         * Google's documented form for Android clients: the package name as the
         * scheme, then `:/` and a path (no `//` authority). Requires "Enable
         * custom URI scheme" on the Android client in Google Cloud Console.
         */
        const val REDIRECT_URI: String = "com.gankdat.pomegranate:/oauth2redirect"
        const val SCOPE: String = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
        const val AUTH_ENDPOINT: String = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT: String = "https://oauth2.googleapis.com/token"
        const val REVOKE_ENDPOINT: String = "https://oauth2.googleapis.com/revoke"
    }
}
