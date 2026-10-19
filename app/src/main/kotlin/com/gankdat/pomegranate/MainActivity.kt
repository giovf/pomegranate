package com.gankdat.pomegranate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
internal fun PomegranateApp() {
    val navController = rememberNavController()
    PomegranateNavHost(
        navController = navController,
        startDestination = Screen.Onboarding,
        modifier = Modifier.fillMaxSize(),
    )
}
