package com.gankdat.pomegranate.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    private val urlSafe = Regex("^[A-Za-z0-9_-]+$")

    @Test
    fun `verifier is 43 URL-safe characters and unique`() {
        val a = Pkce.generateVerifier()
        val b = Pkce.generateVerifier()
        assertEquals(43, a.length)
        assertTrue(urlSafe.matches(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `challenge matches the RFC 7636 appendix B example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun `state is URL-safe and unique`() {
        val s = Pkce.generateState()
        assertTrue(urlSafe.matches(s))
        assertNotEquals(s, Pkce.generateState())
    }
}
