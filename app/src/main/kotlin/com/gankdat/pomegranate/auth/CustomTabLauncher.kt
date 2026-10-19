package com.gankdat.pomegranate.auth

import android.app.Activity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Opens the consent URL in a Chrome Custom Tab (falls back to the default browser). */
internal object CustomTabLauncher {
    fun open(activity: Activity, url: String) {
        CustomTabsIntent.Builder().build().launchUrl(activity, url.toUri())
    }
}
