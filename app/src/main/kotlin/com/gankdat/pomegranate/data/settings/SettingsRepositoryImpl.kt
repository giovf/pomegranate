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
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_AUTO_DISCONNECT
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_CONCURRENCY
import com.gankdat.pomegranate.data.settings.SettingsRepository.Companion.DEFAULT_FOLDER_STRATEGY
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

    override val hasRequestedMediaPermission: Flow<Boolean> = select { it[HAS_REQUESTED_MEDIA_PERMISSION] ?: false }

    override val partialAccessBannerDismissed: Flow<Boolean> = select { it[PARTIAL_ACCESS_BANNER_DISMISSED] ?: false }

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

    override suspend fun setHasRequestedMediaPermission(requested: Boolean) {
        dataStore.edit { it[HAS_REQUESTED_MEDIA_PERMISSION] = requested }
    }

    override suspend fun setPartialAccessBannerDismissed(dismissed: Boolean) {
        dataStore.edit { it[PARTIAL_ACCESS_BANNER_DISMISSED] = dismissed }
    }

    override suspend fun setLastDeviceScanTimeMillis(timeMillis: Long) {
        dataStore.edit { it[LAST_DEVICE_SCAN_TIME] = timeMillis }
    }

    private fun <T> select(read: (Preferences) -> T): Flow<T> = dataStore.data.map(read).distinctUntilChanged()

    private companion object {
        val DESTINATION_ROOT_URI = stringPreferencesKey("destination_root_uri")
        val FOLDER_STRATEGY = stringPreferencesKey("folder_strategy")
        val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_DISCONNECT = booleanPreferencesKey("auto_disconnect")
        val HAS_REQUESTED_MEDIA_PERMISSION = booleanPreferencesKey("has_requested_media_permission")
        val PARTIAL_ACCESS_BANNER_DISMISSED = booleanPreferencesKey("partial_access_banner_dismissed")
        val LAST_DEVICE_SCAN_TIME = longPreferencesKey("last_device_scan_time_millis")
    }
}
