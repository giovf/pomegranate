package com.gankdat.pomegranate.ui.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads MediaStore thumbnails through the ContentResolver and keeps them in
 * a memory-bounded LRU cache (an eighth of the heap). Platform APIs only.
 */
internal class ThumbnailLoader(private val resolver: ContentResolver) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(MIN_CACHE_BYTES)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(uri: Uri, size: Size = DEFAULT_SIZE): Bitmap? {
        val key = "$uri@${size.width}x${size.height}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                resolver.loadThumbnail(uri, size, null).also { cache.put(key, it) }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        val DEFAULT_SIZE: Size = Size(256, 256)
        private const val MIN_CACHE_BYTES = 4 * 1024 * 1024

        @Volatile
        private var shared: ThumbnailLoader? = null

        /** One cache per process; the resolver is application-scoped. */
        fun shared(resolver: ContentResolver): ThumbnailLoader =
            shared ?: synchronized(this) { shared ?: ThumbnailLoader(resolver).also { shared = it } }
    }
}

@Composable
internal fun rememberThumbnailLoader(): ThumbnailLoader {
    val resolver = LocalContext.current.applicationContext.contentResolver
    return remember(resolver) { ThumbnailLoader.shared(resolver) }
}
