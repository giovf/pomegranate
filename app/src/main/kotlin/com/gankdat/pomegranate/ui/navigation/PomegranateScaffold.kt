package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gankdat.pomegranate.PomegranateApplication
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.theme.MediaStateColors
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Standard chrome for a screen: a top bar whose back arrow appears only when
 * there is somewhere to go back to, and content that respects edge-to-edge
 * insets through the padding it receives.
 */
@Composable
internal fun PomegranateScaffold(
    navController: NavHostController,
    title: String,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val app = PomegranateApplication.from(LocalContext.current)
    val isConnected by app.auth.isAuthenticated.collectAsStateWithLifecycle(initialValue = false)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val canNavigateBack = !Screen.isRoot(currentRoute) && navController.previousBackStackEntry != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                PomegranateTopBar(
                    title = title,
                    canNavigateBack = canNavigateBack,
                    onNavigateBack = { navController.popBackStack() },
                    actions = {
                        ConnectionDot(isConnected = isConnected, onClick = { navController.navigateToConnection() })
                        actions()
                    },
                )
            }
        },
        content = content,
    )
}

/** Small green/grey dot in every top bar: tap to open the connection screen. */
@Composable
private fun ConnectionDot(isConnected: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (isConnected) R.string.status_dot_connected else R.string.status_dot_disconnected)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isConnected) MediaStateColors.of(MediaState.BOTH) else MaterialTheme.colorScheme.outline),
        )
    }
}
