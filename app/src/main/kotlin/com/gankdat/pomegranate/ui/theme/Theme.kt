package com.gankdat.pomegranate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PomegranateRed = Color(0xFF9B1B30)
private val PomegranateRedLight = Color(0xFFFFB3B8)

private val LightColors = lightColorScheme(primary = PomegranateRed)
private val DarkColors = darkColorScheme(primary = PomegranateRedLight)

/**
 * Material 3 theme: the user's dynamic colours on Android 12+, a pomegranate
 * palette below that, and dark mode following the system setting.
 */
@Composable
internal fun PomegranateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
