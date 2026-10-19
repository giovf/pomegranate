package com.gankdat.pomegranate.auth

import android.app.Activity
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OAuth 2.0 authorization-code flow with PKCE for a public client. The
 * pending verifier and anti-CSRF state are persisted (encrypted) so the
 * exchange survives the process being killed while the browser is open.
 */
internal class AuthRepositoryImpl(
    private val config: OAuthConfig,
    private val store: TokenStore,
    private val endpoint: TokenEndpoint,
    scope: CoroutineScope,
    private val browser: (Activity, String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newVerifier: () -> String = Pkce::generateVerifier,
    private val newState: () -> String = Pkce::generateState,
) : AuthRepository {
    private sealed interface Phase {
        data object Idle : Phase

        data object Exchanging : Phase

        data object NeedsReauth : Phase

        data class Error(val message: String) : Phase
    }

    private val phase = MutableStateFlow<Phase>(Phase.Idle)
    private val refreshMutex = Mutex()

    override val authState: StateFlow<AuthState> = combine(store.observe(), phase) { record, phase ->
        when {
            !config.isConfigured -> AuthState.NotConfigured
            phase is Phase.Error -> AuthState.Error(phase.message)
            phase is Phase.NeedsReauth -> AuthState.NeedsReauth
            phase is Phase.Exchanging || record.pending != null -> AuthState.SigningIn
            record.tokens != null -> AuthState.SignedIn
            else -> AuthState.SignedOut
        }
    }.stateIn(scope, SharingStarted.Eagerly, if (config.isConfigured) AuthState.SignedOut else AuthState.NotConfigured)

    override val isAuthenticated: Flow<Boolean> = store.observe().map { it.tokens != null }.distinctUntilChanged()

    override val tokenStatus: Flow<TokenStatus?> = store.observe().map { record ->
        record.tokens?.let { TokenStatus(it.expiresAtMillis, it.signedInAtMillis, it.refreshToken != null) }
    }.distinctUntilChanged()

    private val launchScope = scope

    override fun launchSignIn(activity: Activity) {
        if (!config.isConfigured) return
        launchScope.launch {
            val url = beginAuthorization()
            browser(activity, url)
        }
    }

    /** Creates and persists a fresh PKCE pair, returning the consent URL. */
    suspend fun beginAuthorization(): String {
        val pending = PendingAuthorization(codeVerifier = newVerifier(), state = newState())
        store.update { it.copy(pending = pending) }
        phase.value = Phase.Idle
        return authorizationUrl(pending)
    }

    fun authorizationUrl(pending: PendingAuthorization): String {
        val params = linkedMapOf(
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to config.scope,
            "code_challenge" to Pkce.challenge(pending.codeVerifier),
            "code_challenge_method" to Pkce.CHALLENGE_METHOD,
            "state" to pending.state,
            "access_type" to "offline",
            "prompt" to "consent",
        )
        val query = params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        return "${config.authEndpoint}?$query"
    }

    override suspend fun handleRedirect(redirectUrl: String): AuthResult {
        val params = QueryParams.parse(redirectUrl)
        val pending = store.read().pending
        if (pending == null) return fail("No sign-in in progress")
        if (params["state"] != pending.state) return fail("Sign-in response did not match the request")
        params["error"]?.let { error ->
            store.update { it.copy(pending = null) }
            phase.value = Phase.Idle
            return if (error == "access_denied") AuthResult.Cancelled else fail("Google reported: $error")
        }
        val code = params["code"] ?: return fail("Sign-in response had no code")

        phase.value = Phase.Exchanging
        return try {
            val response = endpoint.exchangeCode(code, pending.codeVerifier)
            val now = clock()
            store.update { it.copy(tokens = response.toStored(now).copy(signedInAtMillis = now), pending = null) }
            phase.value = Phase.Idle
            AuthResult.Success
        } catch (e: TokenEndpointException) {
            store.update { it.copy(pending = null) }
            fail("Google rejected the sign-in (${e.httpStatus})")
        } catch (_: IOException) {
            store.update { it.copy(pending = null) }
            fail("Could not reach Google. Check your connection and try again.")
        }
    }

    override suspend fun refreshToken(): AuthResult = refreshMutex.withLock {
        val tokens = store.read().tokens ?: return needsReauth()
        val refreshToken = tokens.refreshToken ?: return needsReauth()
        try {
            val response = endpoint.refresh(refreshToken)
            store.update { record ->
                record.copy(tokens = response.toStored(clock()).copy(refreshToken = response.refreshToken ?: refreshToken, signedInAtMillis = tokens.signedInAtMillis))
            }
            phase.value = Phase.Idle
            AuthResult.Success
        } catch (e: TokenEndpointException) {
            // invalid_grant covers revoked consent and the 7-day Testing-mode expiry.
            if (e.httpStatus == 400 || e.httpStatus == 401) {
                store.update { it.copy(tokens = null) }
                needsReauth()
            } else {
                fail("Google returned ${e.httpStatus} while refreshing")
            }
        } catch (_: IOException) {
            AuthResult.Error("Could not reach Google to refresh the session")
        }
    }

    override suspend fun getAccessToken(): String? {
        val tokens = store.read().tokens ?: return null
        if (!tokens.isExpired(clock())) return tokens.accessToken
        return when (refreshToken()) {
            AuthResult.Success -> store.read().tokens?.accessToken
            else -> null
        }
    }

    override suspend fun revokeAndClear() {
        val tokens = store.read().tokens
        if (tokens != null) endpoint.revoke(tokens.refreshToken ?: tokens.accessToken)
        store.wipe()
        phase.value = Phase.Idle
    }

    override fun dismissError() {
        if (phase.value is Phase.Error) phase.value = Phase.Idle
    }

    private fun fail(message: String): AuthResult.Error {
        phase.value = Phase.Error(message)
        return AuthResult.Error(message)
    }

    private fun needsReauth(): AuthResult {
        phase.value = Phase.NeedsReauth
        return AuthResult.NeedsReauth
    }

    private fun TokenResponse.toStored(now: Long): StoredTokens = StoredTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = now + expiresInSeconds * 1_000 - EXPIRY_BUFFER_MILLIS,
    )

    private companion object {
        const val EXPIRY_BUFFER_MILLIS = 60_000L
    }
}
