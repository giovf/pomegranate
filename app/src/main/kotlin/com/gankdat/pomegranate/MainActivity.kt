package com.gankdat.pomegranate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gankdat.pomegranate.ui.navigation.PomegranateNavHost
import com.gankdat.pomegranate.ui.navigation.Screen
import com.gankdat.pomegranate.ui.theme.PomegranateTheme

internal class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PomegranateTheme {
                PomegranateApp()
            }
        }
    }
}

/** Picks the start destination once onboarding state is known; shows a blank surface until then to avoid a flash. */
@Composable
internal fun PomegranateApp() {
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
    }
}
