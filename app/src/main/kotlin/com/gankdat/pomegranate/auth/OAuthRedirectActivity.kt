package com.gankdat.pomegranate.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.gankdat.pomegranate.MainActivity
import com.gankdat.pomegranate.PomegranateApplication

/**
 * Receives `com.gankdat.pomegranate:/oauth2redirect?...` from the browser,
 * hands the URL to the auth repository and returns to the app. It renders
 * nothing and finishes immediately.
 */
internal class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { PomegranateApplication.from(this).onOAuthRedirect(it.toString()) }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
