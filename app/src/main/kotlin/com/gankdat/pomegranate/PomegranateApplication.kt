package com.gankdat.pomegranate

import android.app.Application
import android.content.Context
import com.gankdat.pomegranate.data.db.PomegranateDatabase
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.data.settings.SettingsRepositoryImpl
import com.gankdat.pomegranate.data.settings.settingsDataStore

/**
 * Process-wide object graph. Deliberately a plain service locator: the app is
 * one module with a handful of singletons, which does not justify a DI
 * framework under the platform + AndroidX dependency policy.
 */
internal class PomegranateApplication : Application() {
    val database: PomegranateDatabase by lazy { PomegranateDatabase.getInstance(this) }

    val settings: SettingsRepository by lazy { SettingsRepositoryImpl(settingsDataStore) }

    companion object {
        fun from(context: Context): PomegranateApplication = context.applicationContext as PomegranateApplication
    }
}
