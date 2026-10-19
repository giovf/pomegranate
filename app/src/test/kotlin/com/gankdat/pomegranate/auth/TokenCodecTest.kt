package com.gankdat.pomegranate.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenCodecTest {
    @Test
    fun `round-trips a full record`() {
        val record = AuthRecord(
            tokens = StoredTokens(accessToken = "ya29.a\"b", refreshToken = "1//r", expiresAtMillis = 1_700_000_000_000),
            pending = PendingAuthorization(codeVerifier = "v", state = "s"),
        )
        assertEquals(record, TokenCodec.decode(TokenCodec.encode(record)))
    }

    @Test
    fun `round-trips partial and empty records`() {
        val noRefresh = AuthRecord(tokens = StoredTokens("a", null, 5))
        assertEquals(noRefresh, TokenCodec.decode(TokenCodec.encode(noRefresh)))
        val pendingOnly = AuthRecord(pending = PendingAuthorization("v", "s"))
        assertEquals(pendingOnly, TokenCodec.decode(TokenCodec.encode(pendingOnly)))
        assertEquals(AuthRecord(), TokenCodec.decode(TokenCodec.encode(AuthRecord())))
    }

    @Test
    fun `corrupt bytes decode to an empty record`() {
        assertEquals(AuthRecord(), TokenCodec.decode("not json".toByteArray()))
        assertEquals(AuthRecord(), TokenCodec.decode("""{"tokens":{"refreshToken":"only"}}""".toByteArray()))
    }
}
