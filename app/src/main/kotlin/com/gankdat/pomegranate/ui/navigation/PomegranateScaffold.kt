package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                    actions = actions,
                )
            }
        },
        content = content,
    )
}
