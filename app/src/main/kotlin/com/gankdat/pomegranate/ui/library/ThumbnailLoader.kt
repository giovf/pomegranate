package com.gankdat.pomegranate.ui.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gankdat.pomegranate.PomegranateApplication
import com.gankdat.pomegranate.census.device.DeviceScanner
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Re-lists a Picker session and returns the item's fresh base URL, or null if that is impossible (session gone, signed out, offline). */
internal fun interface BaseUrlRefresher {
    suspend fun refresh(sessionId: String, cloudItemId: Long): String?
}

/**
 * Loads thumbnails for device items (ContentResolver) and cloud-only items
 * (the Picker base URL on Google's media host, sized server-side), sharing
 * one memory-bounded LRU cache of an eighth of the heap. A cloud link that
 * Google rejects (expired after 60 minutes) is refreshed once and retried.
 * Loads are cancelled with the composable that requested them. Platform
 * APIs only.
 */
internal class ThumbnailLoader(
    private val resolver: ContentResolver,
    private val refresher: BaseUrlRefresher,
    /** Google requires the user's bearer token on picked items' base URLs. */
    private val accessToken: suspend () -> String?,
) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(MIN_CACHE_BYTES)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(source: ThumbnailSource, size: Size = DEFAULT_SIZE): Bitmap? {
        val key = cacheKey(source, size)
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                when (source) {
                    is ThumbnailSource.Local -> resolver.loadThumbnail(DeviceScanner.contentUri(source.volume, source.mediaStoreId), size, null)
                    is ThumbnailSource.Remote -> loadRemote(source, size)
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return bitmap?.also { cache.put(key, it) }
    }

    private suspend fun loadRemote(source: ThumbnailSource.Remote, size: Size): Bitmap? {
        val token = accessToken() ?: return null
        return when (val first = fetch(remoteUrl(source.baseUrl, size), token)) {
            is Fetch.Ok -> first.bitmap
            Fetch.Rejected -> {
                val fresh = refresher.refresh(source.sessionId, source.cloudItemId) ?: return null
                (fetch(remoteUrl(fresh, size), accessToken() ?: return null) as? Fetch.Ok)?.bitmap
            }
            Fetch.Failed -> null
        }
    }

    private sealed interface Fetch {
        data class Ok(val bitmap: Bitmap) : Fetch

        /** 401/403/404: the signed link is no longer valid. */
        data object Rejected : Fetch

        data object Failed : Fetch
    }

    private fun fetch(url: String, token: String): Fetch {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.use { BitmapFactory.decodeStream(it) }?.let { Fetch.Ok(it) } ?: Fetch.Failed
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND -> Fetch.Rejected
                else -> Fetch.Failed
            }
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

        /** Cloud entries are keyed by item, not URL, so a refreshed link reuses the cached bitmap. */
        fun cacheKey(source: ThumbnailSource, size: Size): String = when (source) {
            is ThumbnailSource.Local -> "local:${source.volume}:${source.mediaStoreId}@${size.width}x${size.height}"
            is ThumbnailSource.Remote -> "cloud:${source.cloudItemId}@${size.width}x${size.height}"
        }

        @Volatile
        private var shared: ThumbnailLoader? = null

        /** Drops every cached bitmap, including cloud thumbnails, on Disconnect. */
        fun clearShared() {
            shared?.clear()
        }

        /** One cache per process. */
        fun shared(app: PomegranateApplication): ThumbnailLoader =
            shared ?: synchronized(this) {
                shared ?: ThumbnailLoader(
                    resolver = app.contentResolver,
                    refresher = { sessionId, cloudItemId ->
                        try {
                            app.cloudScanner.refreshBaseUrls(sessionId)
                            app.database.cloudItemDao().getById(cloudItemId)?.baseUrl
                        } catch (_: IOException) {
                            null
                        } catch (_: com.gankdat.pomegranate.census.cloud.PickerApiException) {
                            null
                        } catch (_: com.gankdat.pomegranate.census.cloud.PickerAuthException) {
                            null
                        }
                    },
                    accessToken = { app.auth.getAccessToken() },
                ).also { shared = it }
            }
    }
}

@Composable
internal fun rememberThumbnailLoader(): ThumbnailLoader {
    val app = PomegranateApplication.from(LocalContext.current)
    return remember(app) { ThumbnailLoader.shared(app) }
}
