package com.gankdat.pomegranate.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gankdat.pomegranate.data.model.FolderStrategy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryImplTest {
    @get:Rule
    val folder: TemporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun createStore() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = newDataStore()
        settings = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun closeStore() {
        scope.cancel()
    }

    @Test
    fun `first read returns documented defaults`() = runBlocking {
        assertNull(settings.destinationRootUri.first())
        assertEquals(FolderStrategy.BY_ORIGIN, settings.folderStrategy.first())
        assertEquals(3, settings.downloadConcurrency.first())
        assertTrue(settings.wifiOnly.first())
        assertFalse(settings.autoDisconnect.first())
        assertFalse(settings.hasRequestedMediaPermission.first())
        assertFalse(settings.partialAccessBannerDismissed.first())
        assertEquals(0L, settings.lastDeviceScanTimeMillis.first())
        assertFalse(settings.onboardingCompleted.first())
    }

    @Test
    fun `onboarding completion persists`() = runBlocking {
        settings.setOnboardingCompleted(true)
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun `last device scan time persists`() = runBlocking {
        settings.setLastDeviceScanTimeMillis(1_700_000_000_000)
        assertEquals(1_700_000_000_000L, settings.lastDeviceScanTimeMillis.first())
    }

    @Test
    fun `permission bookkeeping flags persist`() = runBlocking {
        settings.setHasRequestedMediaPermission(true)
        assertTrue(settings.hasRequestedMediaPermission.first())

        settings.setPartialAccessBannerDismissed(true)
        assertTrue(settings.partialAccessBannerDismissed.first())
        settings.setPartialAccessBannerDismissed(false)
        assertFalse(settings.partialAccessBannerDismissed.first())
    }

    @Test
    fun `destination root URI can be set and cleared`() = runBlocking {
        settings.setDestinationRootUri("content://com.android.externalstorage.documents/tree/primary%3APhotos")
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3APhotos", settings.destinationRootUri.first())

        settings.setDestinationRootUri(null)
        assertNull(settings.destinationRootUri.first())
    }

    @Test
    fun `every folder strategy persists`() = runBlocking {
        FolderStrategy.entries.forEach { strategy ->
            settings.setFolderStrategy(strategy)
            assertEquals(strategy, settings.folderStrategy.first())
        }
    }

    @Test
    fun `download concurrency is clamped to the allowed range`() = runBlocking {
        settings.setDownloadConcurrency(5)
        assertEquals(5, settings.downloadConcurrency.first())

        settings.setDownloadConcurrency(0)
        assertEquals(SettingsRepository.MIN_CONCURRENCY, settings.downloadConcurrency.first())

        settings.setDownloadConcurrency(100)
        assertEquals(SettingsRepository.MAX_CONCURRENCY, settings.downloadConcurrency.first())
    }

    @Test
    fun `boolean settings toggle`() = runBlocking {
        settings.setWifiOnly(false)
        assertFalse(settings.wifiOnly.first())
        settings.setWifiOnly(true)
        assertTrue(settings.wifiOnly.first())

        settings.setAutoDisconnect(true)
        assertTrue(settings.autoDisconnect.first())
        settings.setAutoDisconnect(false)
        assertFalse(settings.autoDisconnect.first())
    }

    @Test
    fun `flows emit the current value and then every change`() = runBlocking {
        val emissions = Channel<Int>(Channel.UNLIMITED)
        val collector = scope.launch { settings.downloadConcurrency.collect { emissions.send(it) } }

        assertEquals(3, emissions.receive())
        settings.setDownloadConcurrency(4)
        assertEquals(4, emissions.receive())
        settings.setDownloadConcurrency(6)
        assertEquals(6, emissions.receive())

        collector.cancel()
    }

    @Test
    fun `values survive a new repository on the same file`() = runBlocking {
        settings.setFolderStrategy(FolderStrategy.BY_MONTH_ONLY)
        settings.setWifiOnly(false)
        scope.cancel()

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reopened = SettingsRepositoryImpl(newDataStore())

        assertEquals(FolderStrategy.BY_MONTH_ONLY, reopened.folderStrategy.first())
        assertFalse(reopened.wifiOnly.first())
    }

    @Test
    fun `an unknown stored folder strategy falls back to the default`() = runBlocking {
        settings.setFolderStrategy(FolderStrategy.BY_MONTH_ONLY)
        // Simulate a value written by a future version of the app.
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[stringPreferencesKey("folder_strategy")] = "BY_ALBUM" }
        }

        assertEquals(FolderStrategy.BY_ORIGIN, settings.folderStrategy.first())
    }

    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
}
