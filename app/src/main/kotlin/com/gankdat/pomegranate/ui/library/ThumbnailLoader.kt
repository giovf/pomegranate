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

    /** [httpStatus] is what Google answered, when that is all there is to go on. */
    data class Failed(val reason: PreviewFailure, val httpStatus: Int? = null) : PreviewResult
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
    /** Whether Google has closed the round (session) an item came from; null when unknown. */
    private val roundClosed: suspend (sessionId: String) -> Boolean? = { null },
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
            is Fetch.Rejected -> {
                // A closed round is the usual reason, and re-listing it cannot help: say so instead of trying.
                if (roundClosed(source.sessionId) == true) return PreviewResult.Failed(PreviewFailure.ROUND_CLOSED)
                when (val refreshed = refresher.refresh(source.sessionId, source.cloudItemId)) {
                    is RefreshOutcome.Failed -> PreviewResult.Failed(refreshed.reason, first.status)
                    is RefreshOutcome.Fresh -> {
                        val retryToken = accessToken() ?: return PreviewResult.Failed(PreviewFailure.SIGNED_OUT)
                        when (val second = fetch(remoteUrl(refreshed.baseUrl, size), retryToken)) {
                            is Fetch.Ok -> PreviewResult.Ok(second.bitmap)
                            is Fetch.Rejected -> PreviewResult.Failed(PreviewFailure.OTHER, second.status)
                            is Fetch.Failed -> PreviewResult.Failed(PreviewFailure.OTHER, second.status)
                        }
                    }
                }
            }
            is Fetch.Failed -> PreviewResult.Failed(PreviewFailure.OTHER, first.status)
        }
    }

    private sealed interface Fetch {
        data class Ok(val bitmap: Bitmap) : Fetch

        /** Any 4xx: Google will not serve this link (any more). */
        data class Rejected(val status: Int) : Fetch

        /** A server error, or a body that is not an image; [status] is null for the latter. */
        data class Failed(val status: Int?) : Fetch
    }

    private fun fetch(url: String, token: String): Fetch {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            return when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.use { BitmapFactory.decodeStream(it) }?.let { Fetch.Ok(it) } ?: Fetch.Failed(null)
                in CLIENT_ERRORS -> Fetch.Rejected(status)
                else -> Fetch.Failed(status)
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
        private val CLIENT_ERRORS = 400..499

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
                    roundClosed = { sessionId -> if (app.cloudScanner.isRoundKnownClosed(sessionId)) true else app.cloudScanner.isRoundClosed(sessionId) },
                ).also { shared = it }
            }
    }
}

@Composable
internal fun rememberThumbnailLoader(): ThumbnailLoader {
    val app = PomegranateApplication.from(LocalContext.current)
    return remember(app) { ThumbnailLoader.shared(app) }
}
