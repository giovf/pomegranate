package com.gankdat.pomegranate.ui.onboarding

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/** The few ContentResolver calls the onboarding flow needs, behind an interface for tests. */
internal interface DestinationAccess {
    /** Keeps read/write access across reboots. Throws [SecurityException] if the grant is not persistable. */
    fun takePersistable(uri: Uri)

    /** True while a persisted read+write grant for [uri] exists. */
    fun isPersisted(uri: Uri): Boolean

    fun treeDocumentId(uri: Uri): String
}

internal class ResolverDestinationAccess(private val resolver: ContentResolver) : DestinationAccess {
    override fun takePersistable(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    override fun isPersisted(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission }

    override fun treeDocumentId(uri: Uri): String = DocumentsContract.getTreeDocumentId(uri)
}
