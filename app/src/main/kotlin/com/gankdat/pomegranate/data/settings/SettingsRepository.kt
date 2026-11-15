package com.gankdat.pomegranate.data.settings

import com.gankdat.pomegranate.data.model.FolderStrategy
import com.gankdat.pomegranate.data.model.ThemeMode
import com.gankdat.pomegranate.data.model.MatchDecision
import kotlinx.coroutines.flow.Flow

/**
 * User-facing settings, persisted on device only. Every flow emits the
 * current value immediately and again on each change.
 */
public interface SettingsRepository {
    /** Persisted SAF tree URI of the destination root, or null until the user picks one. */
    public val destinationRootUri: Flow<String?>

    public val folderStrategy: Flow<FolderStrategy>

    /** Light, dark, or whatever the phone is set to. */
    public val themeMode: Flow<ThemeMode>

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

    /** Per picker round: how many of its items were already indexed from an earlier round. */
    public val roundOverlaps: Flow<Map<String, Int>>

    /** The user's verdicts on uncertain matches; references Google ids, so cleared on Disconnect. */
    public val matchDecisions: Flow<List<MatchDecision>>

    /** Version of the census rules the stored index was built with; a newer app rescans everything once. */
    public val censusFormatVersion: Flow<Int>

    /** Epoch millis when the last device census finished, 0 if never. */
    public val lastDeviceScanTimeMillis: Flow<Long>

    public suspend fun setDestinationRootUri(uri: String?)

    public suspend fun setFolderStrategy(strategy: FolderStrategy)

    public suspend fun setThemeMode(mode: ThemeMode)

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

    public suspend fun setRoundOverlap(sessionId: String, alreadyIndexed: Int)

    /** Forgets all round statistics; part of Disconnect. */
    public suspend fun clearRoundOverlaps()

    /** Records a verdict, replacing any earlier one for the same pair. */
    public suspend fun recordMatchDecision(decision: MatchDecision)

    /** Forgets every verdict; part of Disconnect. */
    public suspend fun clearMatchDecisions()

    public suspend fun setOnboardingCompleted(completed: Boolean)

    public suspend fun setFidelityNoticeAcknowledged(acknowledged: Boolean)

    public companion object {
        public const val MIN_CONCURRENCY: Int = 1
        public const val MAX_CONCURRENCY: Int = 8
        public const val DEFAULT_CONCURRENCY: Int = 3
        public val DEFAULT_FOLDER_STRATEGY: FolderStrategy = FolderStrategy.BY_ORIGIN
        public val DEFAULT_THEME_MODE: ThemeMode = ThemeMode.AUTO
        public const val DEFAULT_WIFI_ONLY: Boolean = true
        public const val DEFAULT_AUTO_DISCONNECT: Boolean = false
        public const val DEFAULT_PAUSE_ON_LOW_BATTERY: Boolean = true
    }
}
