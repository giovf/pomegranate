package com.gankdat.pomegranate.transfer

import android.content.Context
import android.media.MediaScannerConnection

/** Makes a pulled file visible to gallery apps. Best effort: SAF providers other than external storage have no path to scan. */
internal fun interface MediaStoreRegistrar {
    fun register(documentId: String, mimeType: String)
}

internal class ScannerMediaStoreRegistrar(context: Context) : MediaStoreRegistrar {
    private val appContext = context.applicationContext

    override fun register(documentId: String, mimeType: String) {
        val path = SafPaths.toFilePath(documentId) ?: return
        MediaScannerConnection.scanFile(appContext, arrayOf(path), arrayOf(mimeType.ifBlank { null }), null)
    }
}
