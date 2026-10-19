package com.gankdat.pomegranate.auth

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface AuthRepository {
    val authState: StateFlow<AuthState>

    val isAuthenticated: Flow<Boolean>

    /** Opens Google's consent screen in a Custom Tab. */
    fun launchSignIn(activity: Activity)

    /** Completes sign-in from the redirect URL delivered to the redirect activity. */
    suspend fun handleRedirect(redirectUrl: String): AuthResult

    suspend fun refreshToken(): AuthResult

    /** A valid access token, refreshing if needed; null when the user must sign in again. */
    suspend fun getAccessToken(): String?

    /** Revokes at Google (best effort) and wipes every local trace, including the Keystore key. */
    suspend fun revokeAndClear()

    /** Clears a transient [AuthState.Error] back to the stored state. */
    fun dismissError()
}
