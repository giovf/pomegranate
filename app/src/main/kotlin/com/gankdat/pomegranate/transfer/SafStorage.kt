package com.gankdat.pomegranate.transfer

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * The Storage Access Framework operations a transfer needs, behind an
 * interface so the engine can be tested against an in-memory tree. Nothing
 * here ever deletes anything except a file this app itself just wrote and
 * failed to verify.
 */
internal interface SafStorage {
    /** Child folder named [name] under [parent], created if missing. */
    fun findOrCreateFolder(parent: String, name: String): String

    fun childExists(parent: String, name: String): Boolean

    /** Creates an empty document and returns its URI. */
    fun createFile(parent: String, mimeType: String, name: String): String

    fun openWrite(uri: String): OutputStream

    fun openRead(uri: String): InputStream

    /** Size as the provider reports it, or null if unknown. */
    fun size(uri: String): Long?

    /** Removes a document this app created; used only to discard an unverified write. */
    fun deleteOwnFile(uri: String)

    /** Document id of [uri], e.g. `primary:Pomegranate/2024`. */
    fun documentId(uri: String): String

    /** Free bytes on the volume holding the root, or null when it cannot be determined. */
    fun freeBytes(rootTreeUri: String): Long?
}

internal class ResolverSafStorage(private val resolver: ContentResolver) : SafStorage {
    override fun findOrCreateFolder(parent: String, name: String): String {
        findChild(parent, name)?.let { return it }
        val parentDoc = toDocumentUri(parent)
        return DocumentsContract.createDocument(resolver, parentDoc, DocumentsContract.Document.MIME_TYPE_DIR, name)?.toString()
            ?: throw TransferException.Storage("Could not create folder $name")
    }

    override fun childExists(parent: String, name: String): Boolean = findChild(parent, name) != null

    override fun createFile(parent: String, mimeType: String, name: String): String =
        DocumentsContract.createDocument(resolver, toDocumentUri(parent), mimeType.ifBlank { "application/octet-stream" }, name)?.toString()
            ?: throw TransferException.Storage("Could not create file $name")

    override fun openWrite(uri: String): OutputStream =
        resolver.openOutputStream(uri.toUri(), "w") ?: throw TransferException.Storage("Could not open file for writing")

    override fun openRead(uri: String): InputStream =
        resolver.openInputStream(uri.toUri()) ?: throw TransferException.Storage("Could not open file for reading")

    override fun size(uri: String): Long? = resolver.query(uri.toUri(), arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }

    override fun deleteOwnFile(uri: String) {
        try {
            DocumentsContract.deleteDocument(resolver, uri.toUri())
        } catch (_: FileNotFoundException) {
            // Already gone.
        }
    }

    override fun documentId(uri: String): String {
        val parsed = uri.toUri()
        return if (DocumentsContract.isDocumentUri(null, parsed) || parsed.pathSegments.contains("document")) {
            DocumentsContract.getDocumentId(parsed)
        } else {
            DocumentsContract.getTreeDocumentId(parsed)
        }
    }

    override fun freeBytes(rootTreeUri: String): Long? = try {
        SafPaths.toFilePath(documentId(rootTreeUri))?.let { StatFs(it).availableBytes }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun findChild(parent: String, name: String): String? {
        val parentDoc = toDocumentUri(parent)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentDoc, DocumentsContract.getDocumentId(parentDoc))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(parentDoc, c.getString(0)).toString()
            }
        }
        return null
    }

    /** Tree URIs (the picked root) become their own document URI; document URIs pass through. */
    private fun toDocumentUri(uri: String): Uri {
        val parsed = uri.toUri()
        return if (parsed.pathSegments.contains("document")) parsed else DocumentsContract.buildDocumentUriUsingTree(parsed, DocumentsContract.getTreeDocumentId(parsed))
    }
}

/** Maps external-storage document ids to filesystem paths for MediaStore registration; pure. */
internal object SafPaths {
    private const val PRIMARY = "primary"

    /** `primary:Pomegranate/x.jpg` → `/storage/emulated/0/Pomegranate/x.jpg`; `1234-ABCD:x` → `/storage/1234-ABCD/x`; null for other providers. */
    fun toFilePath(documentId: String): String? {
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volume = documentId.substring(0, colon)
        val path = documentId.substring(colon + 1).trimStart('/')
        val root = if (volume == PRIMARY) "/storage/emulated/0" else if (Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}").matches(volume)) "/storage/$volume" else return null
        return if (path.isEmpty()) root else "$root/$path"
    }
}
