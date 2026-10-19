package com.gankdat.pomegranate.auth

import java.io.File
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** XOR "cipher" so the store logic can be tested without the Android Keystore. */
internal class FakeTokenCipher : TokenCipher {
    var key: Byte = 0x5A
    var destroyed = false

    override fun encrypt(plaintext: ByteArray): ByteArray = byteArrayOf(key) + plaintext.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.isEmpty() || ciphertext[0] != key) throw GeneralSecurityException("wrong key")
        return ciphertext.drop(1).map { (it.toInt() xor key.toInt()).toByte() }.toByteArray()
    }

    override fun destroyKey() {
        destroyed = true
    }
}

class FileTokenStoreTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private val cipher = FakeTokenCipher()
    private val file by lazy { File(folder.root, "auth/tokens.bin") }
    private fun store() = FileTokenStore(file, cipher, Dispatchers.Unconfined)

    @Test
    fun `starts empty and persists updates across instances`() = runBlocking {
        val store = store()
        assertEquals(AuthRecord(), store.read())

        val tokens = StoredTokens("a", "r", 10)
        store.update { it.copy(tokens = tokens) }

        assertTrue(file.exists())
        assertFalse(String(file.readBytes()).contains("\"a\"")) // not plaintext
        assertEquals(tokens, store().read().tokens)
    }

    @Test
    fun `observe emits the current record then changes`() = runBlocking {
        val store = store()
        assertEquals(AuthRecord(), store.observe().first())
        store.update { it.copy(pending = PendingAuthorization("v", "s")) }
        assertEquals(PendingAuthorization("v", "s"), store.observe().first().pending)
    }

    @Test
    fun `an empty record removes the file`() = runBlocking {
        val store = store()
        store.update { it.copy(tokens = StoredTokens("a", null, 1)) }
        store.update { AuthRecord() }
        assertFalse(file.exists())
    }

    @Test
    fun `wipe deletes the file and destroys the key`() = runBlocking {
        val store = store()
        store.update { it.copy(tokens = StoredTokens("a", "r", 1)) }
        store.wipe()
        assertFalse(file.exists())
        assertTrue(cipher.destroyed)
        assertEquals(AuthRecord(), store.read())
    }

    @Test
    fun `undecryptable file reads as signed out`() = runBlocking {
        store().update { it.copy(tokens = StoredTokens("a", "r", 1)) }
        cipher.key = 0x11 // key rotated
        assertEquals(AuthRecord(), store().read())
    }
}
