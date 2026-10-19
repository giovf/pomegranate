package com.gankdat.pomegranate

import android.app.Application
import android.content.Context
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.auth.AuthRepositoryImpl
import com.gankdat.pomegranate.auth.CustomTabLauncher
import com.gankdat.pomegranate.auth.FileTokenStore
import com.gankdat.pomegranate.auth.HttpTokenEndpoint
import com.gankdat.pomegranate.auth.KeystoreTokenCipher
import com.gankdat.pomegranate.auth.OAuthConfig
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.CloudScannerRepositoryImpl
import com.gankdat.pomegranate.census.cloud.HttpPickerApi
import com.gankdat.pomegranate.census.cloud.SessionPoller
import com.gankdat.pomegranate.census.device.DeviceScanner
import com.gankdat.pomegranate.census.device.DeviceScannerRepository
import com.gankdat.pomegranate.census.device.DeviceScannerRepositoryImpl
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.data.settings.SettingsRepositoryImpl
import com.gankdat.pomegranate.data.settings.settingsDataStore
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.reconcile.ReconciliationRepositoryImpl
import com.gankdat.pomegranate.reconcile.origin.OriginRepository
import com.gankdat.pomegranate.reconcile.origin.OriginRepositoryImpl
import com.gankdat.pomegranate.trust.ManifestRepository
import com.gankdat.pomegranate.trust.ManifestRepositoryImpl
import com.gankdat.pomegranate.transfer.HttpDownloader
import com.gankdat.pomegranate.transfer.ResolverSafStorage
import com.gankdat.pomegranate.transfer.ScannerMediaStoreRegistrar
import com.gankdat.pomegranate.transfer.TransferEngine
import com.gankdat.pomegranate.transfer.TransferQueue
import com.gankdat.pomegranate.transfer.TransferQueueImpl
import com.gankdat.pomegranate.transfer.TransferScheduler
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide object graph. Deliberately a plain service locator: the app is
 * one module with a handful of singletons, which does not justify a DI
 * framework under the platform + AndroidX dependency policy.
 */
internal class PomegranateApplication : Application() {
    /** Work that must outlive any single screen, such as finishing a token exchange. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: PomegranateDatabase by lazy { PomegranateDatabase.getInstance(this) }

    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(settingsDataStore) }

    val deviceScanner: DeviceScannerRepository by lazy {
        DeviceScannerRepositoryImpl(DeviceScanner(this), database.deviceItemDao(), settings)
    }

    val auth: AuthRepository by lazy {
        val config = OAuthConfig()
        AuthRepositoryImpl(
            config = config,
            store = FileTokenStore(File(filesDir, "auth/tokens.bin"), KeystoreTokenCipher()),
            endpoint = HttpTokenEndpoint(config),
            scope = appScope,
            browser = CustomTabLauncher::open,
        )
    }

    val cloudScanner: CloudScannerRepository by lazy {
        val api = HttpPickerApi(accessToken = { auth.getAccessToken() })
        CloudScannerRepositoryImpl(api, SessionPoller(api), database.cloudItemDao(), database.pickerRoundDao())
    }

    val reconciliation: ReconciliationRepository by lazy {
        ReconciliationRepositoryImpl(database.cloudItemDao(), database.deviceItemDao(), database.matchDao(), database.mediaGroupDao())
    }

    val origins: OriginRepository by lazy {
        OriginRepositoryImpl(database.deviceItemDao(), database.cloudItemDao(), database.originRuleDao())
    }

    val transferQueue: TransferQueue by lazy { TransferQueueImpl(database.transferJobDao(), database.cloudItemDao()) }

    val transferEngine: TransferEngine by lazy {
        TransferEngine(
            jobs = database.transferJobDao(),
            cloudItems = database.cloudItemDao(),
            settings = settings,
            cloudScanner = cloudScanner,
            reconciliation = reconciliation,
            storage = ResolverSafStorage(contentResolver),
            downloader = HttpDownloader(),
            registrar = ScannerMediaStoreRegistrar(this),
        )
    }

    val transferScheduler: TransferScheduler by lazy { TransferScheduler(this) }

    val manifest: ManifestRepository by lazy {
        ManifestRepositoryImpl(database.transferJobDao(), settings, ResolverSafStorage(contentResolver), appVersion = BuildConfig.VERSION_NAME)
    }

    /** Called by the redirect activity; the exchange runs here so it survives that activity finishing. */
    fun onOAuthRedirect(redirectUrl: String) {
        appScope.launch { auth.handleRedirect(redirectUrl) }
    }

    companion object {
        fun from(context: Context): PomegranateApplication = context.applicationContext as PomegranateApplication
    }
}
