package com.gankdat.pomegranate.trust

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun `known vectors`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".toByteArray()))
        assertEquals(Sha256.hex("abc".toByteArray()), Sha256.hex(ByteArrayInputStream("abc".toByteArray())))
    }

    @Test
    fun `streaming matches one-shot across buffer boundaries and reports progress`() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val seen = mutableListOf<Long>()
        assertEquals(Sha256.hex(data), Sha256.hex(ByteArrayInputStream(data)) { seen += it })
        assertEquals(200_000L, seen.last())
    }
}
