package com.gankdat.pomegranate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gankdat.pomegranate.data.model.MediaState

/**
 * The one colour per [MediaState], used everywhere the state is shown so the
 * meaning never has to be relearned. Not tied to the dynamic palette on
 * purpose: these must stay distinguishable whatever wallpaper the user has.
 */
internal object MediaStateColors {
    private val deviceOnlyLight = Color(0xFF2E7D32)
    private val deviceOnlyDark = Color(0xFF81C784)
    private val cloudOnlyLight = Color(0xFFEF6C00)
    private val cloudOnlyDark = Color(0xFFFFB74D)
    private val bothLight = Color(0xFF1565C0)
    private val bothDark = Color(0xFF64B5F6)

    fun of(state: MediaState, darkTheme: Boolean): Color = when (state) {
        MediaState.DEVICE_ONLY -> if (darkTheme) deviceOnlyDark else deviceOnlyLight
        MediaState.CLOUD_ONLY -> if (darkTheme) cloudOnlyDark else cloudOnlyLight
        MediaState.BOTH -> if (darkTheme) bothDark else bothLight
    }

    @Composable
    fun of(state: MediaState): Color = of(state, isSystemInDarkTheme())
}
