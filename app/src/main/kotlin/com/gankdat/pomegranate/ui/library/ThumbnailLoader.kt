package com.gankdat.pomegranate.ui.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gankdat.pomegranate.census.device.DeviceScanner
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads thumbnails for device items (ContentResolver) and cloud-only items
 * (the Picker base URL on Google's media host, sized server-side), sharing
 * one memory-bounded LRU cache of an eighth of the heap. Platform APIs only.
 */
internal class ThumbnailLoader(private val resolver: ContentResolver) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(MIN_CACHE_BYTES)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(source: ThumbnailSource, size: Size = DEFAULT_SIZE): Bitmap? {
        val key = "$source@${size.width}x${size.height}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = try {
                when (source) {
                    is ThumbnailSource.Local -> resolver.loadThumbnail(DeviceScanner.contentUri(source.volume, source.mediaStoreId), size, null)
                    is ThumbnailSource.Remote -> fetch(remoteUrl(source.baseUrl, size))
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            bitmap?.also { cache.put(key, it) }
        }
    }

    private fun fetch(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        val DEFAULT_SIZE: Size = Size(256, 256)
        private const val MIN_CACHE_BYTES = 4 * 1024 * 1024
        private const val TIMEOUT_MILLIS = 15_000

        /** Picker base URLs take `=w{w}-h{h}-c` for a server-side cropped thumbnail. */
        fun remoteUrl(baseUrl: String, size: Size): String = "$baseUrl=w${size.width}-h${size.height}-c"

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
