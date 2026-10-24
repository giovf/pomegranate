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

    /** Let the system hold transfers while the battery is low. */
    public val pauseOnLowBattery: Flow<Boolean>

    /** Epoch millis when the user confirmed finishing the Google Photos clean-up, 0 if never. */
    public val cleanupConfirmedAtMillis: Flow<Long>

    /** True once the media permission dialog has been shown at least once; disambiguates "never asked" from "denied for good". */
    public val hasRequestedMediaPermission: Flow<Boolean>

    /** The user dismissed the partial-access banner. */
    public val partialAccessBannerDismissed: Flow<Boolean>

    /** The user finished onboarding (destination chosen); routes straight to the library on launch. */
    public val onboardingCompleted: Flow<Boolean>

    /** The user ticked "don't show again" on the fidelity notice. */
    public val fidelityNoticeAcknowledged: Flow<Boolean>

    /** Version of the census rules the stored index was built with; a newer app rescans everything once. */
    public val censusFormatVersion: Flow<Int>

    /** Epoch millis when the last device census finished, 0 if never. */
    public val lastDeviceScanTimeMillis: Flow<Long>

    public suspend fun setDestinationRootUri(uri: String?)

    public suspend fun setFolderStrategy(strategy: FolderStrategy)

    /** Values outside the allowed range are clamped. */
    public suspend fun setDownloadConcurrency(concurrency: Int)

    public suspend fun setWifiOnly(enabled: Boolean)

    public suspend fun setAutoDisconnect(enabled: Boolean)

    public suspend fun setPauseOnLowBattery(enabled: Boolean)

    public suspend fun setCleanupConfirmedAtMillis(timeMillis: Long)

    public suspend fun setHasRequestedMediaPermission(requested: Boolean)

    public suspend fun setPartialAccessBannerDismissed(dismissed: Boolean)

    public suspend fun setLastDeviceScanTimeMillis(timeMillis: Long)

    public suspend fun setCensusFormatVersion(version: Int)

    public suspend fun setOnboardingCompleted(completed: Boolean)

    public suspend fun setFidelityNoticeAcknowledged(acknowledged: Boolean)

    public companion object {
        public const val MIN_CONCURRENCY: Int = 1
        public const val MAX_CONCURRENCY: Int = 8
        public const val DEFAULT_CONCURRENCY: Int = 3
        public val DEFAULT_FOLDER_STRATEGY: FolderStrategy = FolderStrategy.BY_ORIGIN
        public const val DEFAULT_WIFI_ONLY: Boolean = true
        public const val DEFAULT_AUTO_DISCONNECT: Boolean = false
        public const val DEFAULT_PAUSE_ON_LOW_BATTERY: Boolean = true
    }
}
