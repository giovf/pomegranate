package com.gankdat.pomegranate.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.gankdat.pomegranate.R

/** The three places the bottom bar leads to. */
internal enum class BottomTab(val screen: Screen, @param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    HOME(Screen.Home, R.string.tab_home, R.drawable.ic_tab_home),
    PHOTOS(Screen.Library, R.string.tab_photos, R.drawable.ic_tab_photos),
    MORE(Screen.More, R.string.tab_more, R.drawable.ic_tab_more),
}

/**
 * Standard chrome for a screen: a top bar whose back arrow appears only when
 * there is somewhere to go back to, the bottom bar on the three main screens,
 * and content that respects edge-to-edge insets through the padding it receives.
 */
@Composable
internal fun PomegranateScaffold(
    navController: NavHostController,
    title: String,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    /** The main screen being shown, or null for screens reached from one of them (no bottom bar). */
    bottomTab: BottomTab? = null,
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
        bottomBar = {
            if (bottomTab != null) MainTabs(selected = bottomTab, onSelect = navController::navigateToTab)
        },
        content = content,
    )
}

@Composable
private fun MainTabs(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { if (tab != selected) onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.iconRes), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
