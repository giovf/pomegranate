package com.gankdat.pomegranate.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gankdat.pomegranate.data.model.FolderStrategy
import com.gankdat.pomegranate.data.model.MatchDecision
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_AUTO_DISCONNECT
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_CONCURRENCY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_FOLDER_STRATEGY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_PAUSE_ON_LOW_BATTERY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_WIFI_ONLY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.MAX_CONCURRENCY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.MIN_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The app's single settings store, in app-private storage and excluded from backups. */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** [SettingsRepository] backed by Preferences DataStore. */
internal class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val destinationRootUri: Flow<String?> = select { it[DESTINATION_ROOT_URI] }

    override val folderStrategy: Flow<FolderStrategy> = select { prefs ->
        prefs[FOLDER_STRATEGY]?.let { stored -> FolderStrategy.entries.firstOrNull { it.name == stored } }
            ?: DEFAULT_FOLDER_STRATEGY
    }

    override val downloadConcurrency: Flow<Int> = select { prefs ->
        prefs[DOWNLOAD_CONCURRENCY]?.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY) ?: DEFAULT_CONCURRENCY
    }

    override val wifiOnly: Flow<Boolean> = select { it[WIFI_ONLY] ?: DEFAULT_WIFI_ONLY }

    override val autoDisconnect: Flow<Boolean> = select { it[AUTO_DISCONNECT] ?: DEFAULT_AUTO_DISCONNECT }

    override val pauseOnLowBattery: Flow<Boolean> = select { it[PAUSE_ON_LOW_BATTERY] ?: DEFAULT_PAUSE_ON_LOW_BATTERY }

    override val cleanupConfirmedAtMillis: Flow<Long> = select { it[CLEANUP_CONFIRMED_AT] ?: 0L }

    override val hasRequestedMediaPermission: Flow<Boolean> = select { it[HAS_REQUESTED_MEDIA_PERMISSION] ?: false }

    override val partialAccessBannerDismissed: Flow<Boolean> = select { it[PARTIAL_ACCESS_BANNER_DISMISSED] ?: false }

    override val onboardingCompleted: Flow<Boolean> = select { it[ONBOARDING_COMPLETED] ?: false }

    override val fidelityNoticeAcknowledged: Flow<Boolean> = select { it[FIDELITY_NOTICE_ACKNOWLEDGED] ?: false }

    override val roundOverlaps: Flow<Map<String, Int>> = select { decodeOverlaps(it[ROUND_OVERLAPS]) }

    override val matchDecisions: Flow<List<MatchDecision>> = select { decodeDecisions(it[MATCH_DECISIONS]) }

    override val censusFormatVersion: Flow<Int> = select { it[CENSUS_FORMAT_VERSION] ?: 0 }

    override val lastDeviceScanTimeMillis: Flow<Long> = select { it[LAST_DEVICE_SCAN_TIME] ?: 0L }

    override suspend fun setDestinationRootUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(DESTINATION_ROOT_URI) else prefs[DESTINATION_ROOT_URI] = uri
        }
    }

    override suspend fun setFolderStrategy(strategy: FolderStrategy) {
        dataStore.edit { it[FOLDER_STRATEGY] = strategy.name }
    }

    override suspend fun setDownloadConcurrency(concurrency: Int) {
        dataStore.edit { it[DOWNLOAD_CONCURRENCY] = concurrency.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY) }
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[WIFI_ONLY] = enabled }
    }

    override suspend fun setAutoDisconnect(enabled: Boolean) {
        dataStore.edit { it[AUTO_DISCONNECT] = enabled }
    }

    override suspend fun setPauseOnLowBattery(enabled: Boolean) {
        dataStore.edit { it[PAUSE_ON_LOW_BATTERY] = enabled }
    }

    override suspend fun setCleanupConfirmedAtMillis(timeMillis: Long) {
        dataStore.edit { it[CLEANUP_CONFIRMED_AT] = timeMillis }
    }

    override suspend fun setHasRequestedMediaPermission(requested: Boolean) {
        dataStore.edit { it[HAS_REQUESTED_MEDIA_PERMISSION] = requested }
    }

    override suspend fun setPartialAccessBannerDismissed(dismissed: Boolean) {
        dataStore.edit { it[PARTIAL_ACCESS_BANNER_DISMISSED] = dismissed }
    }

    override suspend fun setRoundOverlap(sessionId: String, alreadyIndexed: Int) {
        dataStore.edit { prefs -> prefs[ROUND_OVERLAPS] = encodeOverlaps(decodeOverlaps(prefs[ROUND_OVERLAPS]) + (sessionId to alreadyIndexed)) }
    }

    override suspend fun clearRoundOverlaps() {
        dataStore.edit { it.remove(ROUND_OVERLAPS) }
    }

    override suspend fun recordMatchDecision(decision: MatchDecision) {
        dataStore.edit { prefs ->
            val others = decodeDecisions(prefs[MATCH_DECISIONS]).filterNot {
                it.pickerMediaItemId == decision.pickerMediaItemId && it.volume == decision.volume && it.mediaStoreId == decision.mediaStoreId
            }
            prefs[MATCH_DECISIONS] = encodeDecisions(others + decision)
        }
    }

    override suspend fun clearMatchDecisions() {
        dataStore.edit { it.remove(MATCH_DECISIONS) }
    }

    override suspend fun setCensusFormatVersion(version: Int) {
        dataStore.edit { it[CENSUS_FORMAT_VERSION] = version }
    }

    override suspend fun setLastDeviceScanTimeMillis(timeMillis: Long) {
        dataStore.edit { it[LAST_DEVICE_SCAN_TIME] = timeMillis }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setFidelityNoticeAcknowledged(acknowledged: Boolean) {
        dataStore.edit { it[FIDELITY_NOTICE_ACKNOWLEDGED] = acknowledged }
    }

    private fun <T> select(read: (Preferences) -> T): Flow<T> = dataStore.data.map(read).distinctUntilChanged()

    private companion object {
        /** `id=count` pairs joined by `&`, ids URL-encoded. */
        fun encodeOverlaps(map: Map<String, Int>): String =
            map.entries.joinToString("&") { (id, n) -> java.net.URLEncoder.encode(id, "UTF-8") + "=" + n }

        fun decodeOverlaps(text: String?): Map<String, Int> =
            text.orEmpty().split('&').filter { '=' in it }.mapNotNull { pair ->
                val n = pair.substringAfter('=').toIntOrNull() ?: return@mapNotNull null
                java.net.URLDecoder.decode(pair.substringBefore('='), "UTF-8") to n
            }.toMap()

        /** One decision per line: URL-encoded media item id, URL-encoded volume, MediaStore id, `S` or `D`; tab-separated. */
        fun encodeDecisions(list: List<MatchDecision>): String = list.joinToString("\n") { d ->
            listOf(java.net.URLEncoder.encode(d.pickerMediaItemId, "UTF-8"), java.net.URLEncoder.encode(d.volume, "UTF-8"), d.mediaStoreId.toString(), if (d.same) "S" else "D")
                .joinToString("\t")
        }

        fun decodeDecisions(text: String?): List<MatchDecision> = text.orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@mapNotNull null
            val id = parts[2].toLongOrNull() ?: return@mapNotNull null
            val same = when (parts[3]) {
                "S" -> true
                "D" -> false
                else -> return@mapNotNull null
            }
            MatchDecision(java.net.URLDecoder.decode(parts[0], "UTF-8"), java.net.URLDecoder.decode(parts[1], "UTF-8"), id, same)
        }.toList()

        val DESTINATION_ROOT_URI = stringPreferencesKey("destination_root_uri")
        val FOLDER_STRATEGY = stringPreferencesKey("folder_strategy")
        val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_DISCONNECT = booleanPreferencesKey("auto_disconnect")
        val PAUSE_ON_LOW_BATTERY = booleanPreferencesKey("pause_on_low_battery")
        val CLEANUP_CONFIRMED_AT = longPreferencesKey("cleanup_confirmed_at_millis")
        val HAS_REQUESTED_MEDIA_PERMISSION = booleanPreferencesKey("has_requested_media_permission")
        val PARTIAL_ACCESS_BANNER_DISMISSED = booleanPreferencesKey("partial_access_banner_dismissed")
        val LAST_DEVICE_SCAN_TIME = longPreferencesKey("last_device_scan_time_millis")
        val CENSUS_FORMAT_VERSION = intPreferencesKey("census_format_version")
        val ROUND_OVERLAPS = stringPreferencesKey("round_overlaps")
        val MATCH_DECISIONS = stringPreferencesKey("match_decisions")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FIDELITY_NOTICE_ACKNOWLEDGED = booleanPreferencesKey("fidelity_notice_acknowledged")
    }
}
