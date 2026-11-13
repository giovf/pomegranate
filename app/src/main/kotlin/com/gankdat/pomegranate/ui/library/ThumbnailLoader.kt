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

/** Why a preview could not be shown; each has its own advice for the user. */
internal enum class PreviewFailure {
    /** No valid Google sign-in (never signed in, or the sign-in expired). */
    SIGNED_OUT,

    /** Google closed the picking session the item came from; it must be picked again. */
    ROUND_CLOSED,

    /** Google could not be reached. */
    OFFLINE,

    /** Anything else, including a local file that cannot be read. */
    OTHER,
}

internal sealed interface PreviewResult {
    data class Ok(val bitmap: Bitmap) : PreviewResult

    data class Failed(val reason: PreviewFailure) : PreviewResult
}

internal sealed interface RefreshOutcome {
    data class Fresh(val baseUrl: String) : RefreshOutcome

    data class Failed(val reason: PreviewFailure) : RefreshOutcome
}

/** Re-lists a Picker session and returns the item's fresh base URL, or why that is impossible. */
internal fun interface BaseUrlRefresher {
    suspend fun refresh(sessionId: String, cloudItemId: Long): RefreshOutcome
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

    suspend fun load(source: ThumbnailSource, size: Size = DEFAULT_SIZE): Bitmap? = (loadResult(source, size) as? PreviewResult.Ok)?.bitmap

    /** Like [load], but says why nothing could be shown. */
    suspend fun loadResult(source: ThumbnailSource, size: Size = DEFAULT_SIZE): PreviewResult {
        val key = cacheKey(source, size)
        cache.get(key)?.let { return PreviewResult.Ok(it) }
        val result = withContext(Dispatchers.IO) {
            try {
                when (source) {
                    is ThumbnailSource.Local ->
                        PreviewResult.Ok(resolver.loadThumbnail(DeviceScanner.contentUri(source.volume, source.mediaStoreId), size, null))
                    is ThumbnailSource.Remote -> loadRemote(source, size)
                }
            } catch (_: IOException) {
                PreviewResult.Failed(if (source is ThumbnailSource.Remote) PreviewFailure.OFFLINE else PreviewFailure.OTHER)
            } catch (_: SecurityException) {
                PreviewResult.Failed(PreviewFailure.OTHER)
            } catch (_: IllegalArgumentException) {
                PreviewResult.Failed(PreviewFailure.OTHER)
            }
        }
        if (result is PreviewResult.Ok) cache.put(key, result.bitmap)
        return result
    }

    private suspend fun loadRemote(source: ThumbnailSource.Remote, size: Size): PreviewResult {
        val token = accessToken() ?: return PreviewResult.Failed(PreviewFailure.SIGNED_OUT)
        return when (val first = fetch(remoteUrl(source.baseUrl, size), token)) {
            is Fetch.Ok -> PreviewResult.Ok(first.bitmap)
            Fetch.Rejected -> when (val refreshed = refresher.refresh(source.sessionId, source.cloudItemId)) {
                is RefreshOutcome.Failed -> PreviewResult.Failed(refreshed.reason)
                is RefreshOutcome.Fresh -> {
                    val retryToken = accessToken() ?: return PreviewResult.Failed(PreviewFailure.SIGNED_OUT)
                    (fetch(remoteUrl(refreshed.baseUrl, size), retryToken) as? Fetch.Ok)?.let { PreviewResult.Ok(it.bitmap) } ?: PreviewResult.Failed(PreviewFailure.OTHER)
                }
            }
            Fetch.Failed -> PreviewResult.Failed(PreviewFailure.OTHER)
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
                            app.database.cloudItemDao().getById(cloudItemId)?.baseUrl?.let { RefreshOutcome.Fresh(it) } ?: RefreshOutcome.Failed(PreviewFailure.OTHER)
                        } catch (_: IOException) {
                            RefreshOutcome.Failed(PreviewFailure.OFFLINE)
                        } catch (e: com.gankdat.pomegranate.census.cloud.PickerApiException) {
                            RefreshOutcome.Failed(if (e.isGone) PreviewFailure.ROUND_CLOSED else if (e.isAuthFailure) PreviewFailure.SIGNED_OUT else PreviewFailure.OTHER)
                        } catch (_: com.gankdat.pomegranate.census.cloud.PickerAuthException) {
                            RefreshOutcome.Failed(PreviewFailure.SIGNED_OUT)
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
