package com.gankdat.pomegranate.auth

import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Persistence for [AuthRecord]; implementations encrypt at rest. */
internal interface TokenStore {
    fun observe(): Flow<AuthRecord>

    suspend fun read(): AuthRecord

    suspend fun update(transform: (AuthRecord) -> AuthRecord): AuthRecord

    /** Removes the record and the key material behind it. */
    suspend fun wipe()
}

/**
 * One encrypted file in app-private storage. Reads once, then serves from
 * memory; writes go to a temp file and are renamed into place so a crash
 * never leaves a half-written record.
 */
internal class FileTokenStore(
    private val file: File,
    private val cipher: TokenCipher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TokenStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow<AuthRecord?>(null)

    override fun observe(): Flow<AuthRecord> = kotlinx.coroutines.flow.flow {
        emit(read())
        state.asStateFlow().collect { if (it != null) emit(it) }
    }

    override suspend fun read(): AuthRecord = state.value ?: mutex.withLock { state.value ?: load().also { state.value = it } }

    override suspend fun update(transform: (AuthRecord) -> AuthRecord): AuthRecord = mutex.withLock {
        val current = state.value ?: load()
        val next = transform(current)
        if (next.isEmpty) deleteFile() else save(next)
        state.value = next
        next
    }

    override suspend fun wipe() {
        mutex.withLock {
            deleteFile()
            try {
                cipher.destroyKey()
            } catch (_: GeneralSecurityException) {
                // The key is already gone; nothing to destroy.
            }
            state.value = AuthRecord()
        }
    }

    private suspend fun load(): AuthRecord = withContext(ioDispatcher) {
        if (!file.exists()) return@withContext AuthRecord()
        try {
            TokenCodec.decode(cipher.decrypt(file.readBytes()))
        } catch (_: GeneralSecurityException) {
            AuthRecord() // Key rotated or file tampered: treat as signed out.
        } catch (_: IOException) {
            AuthRecord()
        } catch (_: IllegalArgumentException) {
            AuthRecord()
        }
    }

    private suspend fun save(record: AuthRecord) = withContext(ioDispatcher) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(cipher.encrypt(TokenCodec.encode(record)))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("could not replace token file")
        }
    }

    private suspend fun deleteFile() = withContext(ioDispatcher) {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
        Unit
    }
}
