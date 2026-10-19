package com.gankdat.pomegranate.ui.format

/** Human-readable file-type labels for the MIME types MediaStore reports. */
internal object MimeLabels {
    private val known = mapOf(
        "image/jpeg" to "JPEG image",
        "image/png" to "PNG image",
        "image/gif" to "GIF image",
        "image/webp" to "WebP image",
        "image/heic" to "HEIC image",
        "image/heif" to "HEIF image",
        "image/avif" to "AVIF image",
        "image/x-adobe-dng" to "DNG raw image",
        "image/bmp" to "BMP image",
        "video/mp4" to "MP4 video",
        "video/3gpp" to "3GP video",
        "video/webm" to "WebM video",
        "video/x-matroska" to "MKV video",
        "video/quicktime" to "QuickTime video",
    )

    fun label(mime: String): String = known[mime.lowercase()] ?: when {
        mime.startsWith("image/") -> "${mime.substringAfter('/').uppercase()} image"
        mime.startsWith("video/") -> "${mime.substringAfter('/').uppercase()} video"
        mime.isBlank() -> "Unknown type"
        else -> mime
    }
}
