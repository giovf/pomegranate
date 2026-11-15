package com.gankdat.pomegranate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R

/**
 * Pomegranate's own look: the peach and ruby palette of the app icon, the
 * rounded display face for headings, and soft corners. Dark mode follows the
 * system setting. The owner chose a fixed brand palette over wallpaper-derived
 * colours on 2026-11-15, so every phone shows the same app.
 */
@Composable
internal fun PomegranateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PomegranateTypography,
        shapes = PomegranateShapes,
        content = content,
    )
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFC7203F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF4A0A18),
    inversePrimary = Color(0xFFFFB2BC),
    secondary = Color(0xFF775659),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADC),
    onSecondaryContainer = Color(0xFF2C1517),
    tertiary = Color(0xFF7A5733),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF2A1014),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF2A1014),
    surfaceVariant = Color(0xFFF7E4DB),
    onSurfaceVariant = Color(0xFF6B5357),
    surfaceTint = Color(0xFFC7203F),
    inverseSurface = Color(0xFF3A2528),
    inverseOnSurface = Color(0xFFFFEDE8),
    outline = Color(0xFF9C8178),
    outlineVariant = Color(0xFFF0D8CE),
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFEFD9D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEBE3),
    surfaceContainerHigh = Color(0xFFF8E4DB),
    surfaceContainerHighest = Color(0xFFF3DDD3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB2BC),
    onPrimary = Color(0xFF5E0A1E),
    primaryContainer = Color(0xFF8A1730),
    onPrimaryContainer = Color(0xFFFFD9DD),
    inversePrimary = Color(0xFFC7203F),
    secondary = Color(0xFFE6BDBF),
    onSecondary = Color(0xFF44292C),
    secondaryContainer = Color(0xFF5D3F42),
    onSecondaryContainer = Color(0xFFFFDADC),
    tertiary = Color(0xFFEBBE91),
    onTertiary = Color(0xFF452B0A),
    tertiaryContainer = Color(0xFF5F411E),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF1B1012),
    onBackground = Color(0xFFF6E4E0),
    surface = Color(0xFF1B1012),
    onSurface = Color(0xFFF6E4E0),
    surfaceVariant = Color(0xFF3A2A2C),
    onSurfaceVariant = Color(0xFFD8C1BD),
    surfaceTint = Color(0xFFFFB2BC),
    inverseSurface = Color(0xFFF6E4E0),
    inverseOnSurface = Color(0xFF3A2528),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF4B3A3C),
    surfaceBright = Color(0xFF443436),
    surfaceDim = Color(0xFF1B1012),
    surfaceContainerLowest = Color(0xFF150B0D),
    surfaceContainerLow = Color(0xFF231719),
    surfaceContainer = Color(0xFF281B1D),
    surfaceContainerHigh = Color(0xFF332527),
    surfaceContainerHighest = Color(0xFF3E3032),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * Baloo 2 Bold, Latin subset (SIL Open Font License 1.1; licence text in
 * assets/licenses). Used for headings only; body text stays in the system face.
 */
private val Baloo: FontFamily = FontFamily(Font(R.font.baloo2_bold, FontWeight.Bold))

private val PomegranateTypography: Typography = Typography().let { base ->
    fun heading(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold)
    base.copy(
        displayLarge = heading(base.displayLarge),
        displayMedium = heading(base.displayMedium),
        displaySmall = heading(base.displaySmall),
        headlineLarge = heading(base.headlineLarge),
        headlineMedium = heading(base.headlineMedium),
        headlineSmall = heading(base.headlineSmall),
        titleLarge = heading(base.titleLarge),
    )
}

private val PomegranateShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
