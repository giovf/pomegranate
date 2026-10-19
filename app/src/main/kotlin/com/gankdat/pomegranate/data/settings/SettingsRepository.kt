package com.gankdat.pomegranate.data.settings

import com.gankdat.pomegranate.data.model.FolderStrategy
import kotlinx.coroutines.flow.Flow

/**
 * User-facing settings, persisted on device only. Every flow emits the
 * current value immediately and again on each change.
 */
public interface SettingsRepository {
    /** Persisted SAF tree URI of the destination root, or null until the user picks one. */
    public val destinationRootUri: Flow<String?>

    public val folderStrategy: Flow<FolderStrategy>

    /** Number of simultaneous downloads, between [MIN_CONCURRENCY] and [MAX_CONCURRENCY]. */
    public val downloadConcurrency: Flow<Int>

    /** Only transfer on unmetered networks. */
    public val wifiOnly: Flow<Boolean>

    /** Revoke and wipe automatically once every selected item is verified. */
    public val autoDisconnect: Flow<Boolean>

    /** True once the media permission dialog has been shown at least once; disambiguates "never asked" from "denied for good". */
    public val hasRequestedMediaPermission: Flow<Boolean>

    /** The user dismissed the partial-access banner. */
    public val partialAccessBannerDismissed: Flow<Boolean>

    /** Epoch millis when the last device census finished, 0 if never. */
    public val lastDeviceScanTimeMillis: Flow<Long>

    public suspend fun setDestinationRootUri(uri: String?)

    public suspend fun setFolderStrategy(strategy: FolderStrategy)

    /** Values outside the allowed range are clamped. */
    public suspend fun setDownloadConcurrency(concurrency: Int)

    public suspend fun setWifiOnly(enabled: Boolean)

    public suspend fun setAutoDisconnect(enabled: Boolean)

    public suspend fun setHasRequestedMediaPermission(requested: Boolean)

    public suspend fun setPartialAccessBannerDismissed(dismissed: Boolean)

    public suspend fun setLastDeviceScanTimeMillis(timeMillis: Long)

    public companion object {
        public const val MIN_CONCURRENCY: Int = 1
        public const val MAX_CONCURRENCY: Int = 8
        public const val DEFAULT_CONCURRENCY: Int = 3
        public val DEFAULT_FOLDER_STRATEGY: FolderStrategy = FolderStrategy.BY_ORIGIN
        public const val DEFAULT_WIFI_ONLY: Boolean = true
        public const val DEFAULT_AUTO_DISCONNECT: Boolean = false
    }
}
