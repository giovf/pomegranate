package com.gankdat.pomegranate.auth

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakeEndpoint : TokenEndpoint {
    var exchange: (String, String) -> TokenResponse = { _, _ -> TokenResponse("access-1", "refresh-1", 3_600) }
    var refreshResponse: (String) -> TokenResponse = { TokenResponse("access-2", null, 3_600) }
    val exchanged = mutableListOf<Pair<String, String>>()
    val refreshed = mutableListOf<String>()
    val revoked = mutableListOf<String>()

    override suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse {
        exchanged += code to codeVerifier
        return exchange(code, codeVerifier)
    }

    override suspend fun refresh(refreshToken: String): TokenResponse {
        refreshed += refreshToken
        return refreshResponse(refreshToken)
    }

    override suspend fun revoke(token: String) {
        revoked += token
    }
}

class AuthRepositoryImplTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val config = OAuthConfig(clientId = "client-id")
    private val cipher = FakeTokenCipher()
    private val store by lazy { FileTokenStore(File(folder.root, "tokens.bin"), cipher, Dispatchers.Unconfined) }
    private val endpoint = FakeEndpoint()
    private var now = 1_700_000_000_000L
    private val opened = mutableListOf<String>()
    private val repo by lazy {
        AuthRepositoryImpl(
            config = config,
            store = store,
            endpoint = endpoint,
            scope = scope,
            browser = { _, url -> opened += url },
            clock = { now },
            newVerifier = { "verifier-123" },
            newState = { "state-xyz" },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `authorization URL carries PKCE, state and offline access`() = runBlocking {
        val url = repo.beginAuthorization()
        val params = QueryParams.parse(url)

        assertTrue(url.startsWith(OAuthConfig.AUTH_ENDPOINT + "?"))
        assertEquals("client-id", params["client_id"])
        assertEquals(OAuthConfig.REDIRECT_URI, params["redirect_uri"])
        assertEquals("code", params["response_type"])
        assertEquals(OAuthConfig.SCOPE, params["scope"])
        assertEquals(Pkce.challenge("verifier-123"), params["code_challenge"])
        assertEquals("S256", params["code_challenge_method"])
        assertEquals("state-xyz", params["state"])
        assertEquals("offline", params["access_type"])
        assertEquals("consent", params["prompt"])
        // An unanswered sign-in leaves the Connect button available rather than a spinner.
        assertEquals(AuthState.SignedOut, repo.authState.first())
    }

    @Test
    fun `successful redirect exchanges the code and signs in`() = runBlocking {
        repo.beginAuthorization()

        val result = repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=the-code&scope=x")

        assertEquals(AuthResult.Success, result)
        assertEquals(listOf("the-code" to "verifier-123"), endpoint.exchanged)
        val tokens = store.read().tokens!!
        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(now + 3_600_000 - 60_000, tokens.expiresAtMillis)
        assertEquals(now, tokens.signedInAtMillis)
        assertEquals(TokenStatus(now + 3_600_000 - 60_000, now, hasRefreshToken = true), repo.tokenStatus.first())
        assertNull(store.read().pending)
        assertEquals(AuthState.SignedIn, repo.authState.first())
        assertTrue(repo.isAuthenticated.first())
        assertEquals("access-1", repo.getAccessToken())
    }

    @Test
    fun `a stale pending request from an earlier session still completes if its redirect arrives`() = runBlocking {
        repo.beginAuthorization()
        val restarted = AuthRepositoryImpl(config, store, endpoint, scope, browser = { _, _ -> }, clock = { now })
        assertEquals(AuthState.SignedOut, restarted.authState.first())
        assertEquals(AuthResult.Success, restarted.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=late"))
        assertEquals(AuthState.SignedIn, restarted.authState.first())
    }

    @Test
    fun `redirect with a wrong state is rejected`() = runBlocking {
        repo.beginAuthorization()
        val result = repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=evil&code=the-code")
        assertTrue(result is AuthResult.Error)
        assertTrue(endpoint.exchanged.isEmpty())
        assertTrue(repo.authState.first() is AuthState.Error)
    }

    @Test
    fun `redirect without a pending request is rejected`() = runBlocking {
        assertTrue(repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=c") is AuthResult.Error)
    }

    @Test
    fun `user cancelling consent returns Cancelled and clears the pending request`() = runBlocking {
        repo.beginAuthorization()
        assertEquals(AuthResult.Cancelled, repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&error=access_denied"))
        assertNull(store.read().pending)
        assertEquals(AuthState.SignedOut, repo.authState.first())
    }

    @Test
    fun `network failure during exchange surfaces an error and drops the pending request`() = runBlocking {
        repo.beginAuthorization()
        endpoint.exchange = { _, _ -> throw IOException("offline") }
        assertTrue(repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=c") is AuthResult.Error)
        assertNull(store.read().pending)
        repo.dismissError()
        assertEquals(AuthState.SignedOut, repo.authState.first())
    }

    @Test
    fun `expired access token is refreshed transparently and the refresh token kept`() = runBlocking {
        repo.beginAuthorization()
        repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=c")
        now += 3_600_000

        assertEquals("access-2", repo.getAccessToken())
        assertEquals(listOf("refresh-1"), endpoint.refreshed)
        assertEquals("refresh-1", store.read().tokens?.refreshToken)
        assertEquals(now + 3_600_000 - 60_000, store.read().tokens?.expiresAtMillis)
        assertEquals(now - 3_600_000, store.read().tokens?.signedInAtMillis) // sign-in time survives refresh
    }

    @Test
    fun `invalid_grant on refresh means sign in again`() = runBlocking {
        repo.beginAuthorization()
        repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=c")
        now += 3_600_000
        endpoint.refreshResponse = { throw TokenEndpointException(400, "invalid_grant") }

        assertNull(repo.getAccessToken())
        assertEquals(AuthResult.NeedsReauth, repo.refreshToken())
        assertEquals(AuthState.NeedsReauth, repo.authState.first())
        assertFalse(repo.isAuthenticated.first())
    }

    @Test
    fun `revokeAndClear revokes the refresh token and wipes everything`() = runBlocking {
        repo.beginAuthorization()
        repo.handleRedirect("${OAuthConfig.REDIRECT_URI}?state=state-xyz&code=c")

        repo.revokeAndClear()

        assertEquals(listOf("refresh-1"), endpoint.revoked)
        assertEquals(AuthRecord(), store.read())
        assertTrue(cipher.destroyed)
        assertEquals(AuthState.SignedOut, repo.authState.first())
        assertNull(repo.getAccessToken())
    }

    @Test
    fun `unconfigured client never opens the browser`() = runBlocking {
        val unconfigured = AuthRepositoryImpl(OAuthConfig(clientId = ""), store, endpoint, scope, browser = { _, url -> opened += url })
        assertEquals(AuthState.NotConfigured, unconfigured.authState.first())
    }

    @Test
    fun `token endpoint helpers encode forms and parse responses`() {
        assertEquals("a=1+2&b=%26", HttpTokenEndpoint.formEncode(linkedMapOf("a" to "1 2", "b" to "&")))
        val parsed = HttpTokenEndpoint.parseTokenResponse("""{"access_token":"x","expires_in":3599,"refresh_token":"r","scope":"s","token_type":"Bearer"}""")
        assertEquals(TokenResponse("x", "r", 3_599), parsed)
        assertEquals("invalid_grant", HttpTokenEndpoint.oauthErrorCode("""{"error":"invalid_grant","error_description":"Bad"}"""))
        assertNull(HttpTokenEndpoint.oauthErrorCode("<html>"))
    }
}
