package com.gankdat.pomegranate.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class QueryParamsTest {
    @Test
    fun `decodes values and tolerates missing parts`() {
        assertEquals(mapOf("code" to "a b&c", "state" to "s", "flag" to ""), QueryParams.parse("x://y/z?code=a+b%26c&state=s&flag#frag"))
        assertEquals(emptyMap<String, String>(), QueryParams.parse("x://y/z"))
        assertEquals(emptyMap<String, String>(), QueryParams.parse("x://y/z?"))
    }
}
