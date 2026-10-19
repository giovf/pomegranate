package com.gankdat.pomegranate.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** In-memory document tree keyed by fake URIs. */
internal class FakeSafStorage : SafStorage {
    val folders = linkedMapOf<String, MutableMap<String, String>>() // folderUri -> name -> childUri
    val files = linkedMapOf<String, ByteArrayOutputStream>()
    val deleted = mutableListOf<String>()
    var reportedSize: ((String) -> Long?)? = null
    var failCreateFile = false
    private var next = 1

    init {
        folders["tree:root"] = linkedMapOf()
    }

    override fun findOrCreateFolder(parent: String, name: String): String {
        val children = folders.getValue(parent)
        return children.getOrPut(name) { "folder:${next++}:$name".also { folders[it] = linkedMapOf() } }
    }

    override fun childExists(parent: String, name: String): Boolean = folders.getValue(parent).containsKey(name)

    override fun createFile(parent: String, mimeType: String, name: String): String {
        if (failCreateFile) throw TransferException.Storage("disk full")
        val uri = "file:${next++}:$name"
        folders.getValue(parent)[name] = uri
        files[uri] = ByteArrayOutputStream()
        return uri
    }

    override fun openWrite(uri: String): OutputStream = files.getValue(uri)

    override fun openRead(uri: String): InputStream = ByteArrayInputStream(files.getValue(uri).toByteArray())

    override fun size(uri: String): Long? = reportedSize?.invoke(uri) ?: files[uri]?.size()?.toLong()

    override fun deleteOwnFile(uri: String) {
        deleted += uri
        files.remove(uri)
        folders.values.forEach { it.values.remove(uri) }
    }

    override fun documentId(uri: String): String = "primary:" + uri.substringAfterLast(':')

    override fun freeBytes(rootTreeUri: String): Long? = 1_000_000_000

    fun pathOf(uri: String): String {
        fun parentOf(child: String): Pair<String, String>? = folders.entries.firstNotNullOfOrNull { (folder, kids) -> kids.entries.firstOrNull { it.value == child }?.let { folder to it.key } }
        val parts = ArrayDeque<String>()
        var current = uri
        while (true) {
            val (parent, name) = parentOf(current) ?: break
            parts.addFirst(name)
            current = parent
        }
        return parts.joinToString("/")
    }
}

internal class FakeDownloader : Downloader {
    var bodies = mutableMapOf<String, ByteArray>()
    var failWith: TransferException? = null
    val requested = mutableListOf<String>()

    override fun download(url: String, sink: OutputStream, onProgress: (Long) -> Unit): Long {
        requested += url
        failWith?.let { throw it }
        val body = bodies[url] ?: throw TransferException.SessionGone()
        var written = 0L
        body.toList().chunked(7).forEach { chunk ->
            sink.write(chunk.toByteArray())
            written += chunk.size
            onProgress(written)
        }
        return written
    }
}
