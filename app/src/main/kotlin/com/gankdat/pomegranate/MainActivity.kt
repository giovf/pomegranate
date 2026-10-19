package com.gankdat.pomegranate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gankdat.pomegranate.data.model.PullScope
import com.gankdat.pomegranate.ui.navigation.PomegranateNavHost
import com.gankdat.pomegranate.ui.navigation.Screen
import com.gankdat.pomegranate.ui.navigation.navigateToPull
import com.gankdat.pomegranate.ui.theme.PomegranateTheme

internal class MainActivity : ComponentActivity() {
    /** Incremented per intent so the same request (e.g. the notification tapped twice) is handled each time. */
    private var openPullRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.wantsPull()) openPullRequest++
        setContent {
            PomegranateTheme {
                PomegranateApp(openPullRequest = openPullRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsPull()) openPullRequest++
    }

    private fun Intent.wantsPull(): Boolean = getBooleanExtra(EXTRA_OPEN_PULL, false)

    companion object {
        const val EXTRA_OPEN_PULL: String = "com.gankdat.pomegranate.OPEN_PULL"
    }
}

/** Picks the start destination once onboarding state is known; shows a blank surface until then to avoid a flash. */
@Composable
internal fun PomegranateApp(openPullRequest: Int = 0) {
    val app = PomegranateApplication.from(LocalContext.current)
    val onboardingCompleted by app.settings.onboardingCompleted.collectAsStateWithLifecycle(initialValue = null)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val start = when (onboardingCompleted) {
            null -> return@Surface
            true -> Screen.Library
            false -> Screen.Onboarding
        }
        val navController = rememberNavController()
        PomegranateNavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize(),
        )
        LaunchedEffect(openPullRequest) {
            if (openPullRequest > 0 && start == Screen.Library) navController.navigateToPull(PullScope.CLOUD_ONLY)
        }
    }
}
