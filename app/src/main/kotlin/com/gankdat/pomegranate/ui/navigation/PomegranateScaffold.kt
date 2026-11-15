package com.gankdat.pomegranate.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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

/**
 * The bottom bar, kept low (60dp rather than Material's 80dp) so it takes little
 * of the screen. It sits above the system navigation area; the scaffold hands
 * its height to the content as padding, so nothing is drawn behind it.
 */
@Composable
private fun MainTabs(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLowest) {
        Column {
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(BAR_HEIGHT)
                    .selectableGroup(),
            ) {
                BottomTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { if (!isSelected) onSelect(tab) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) scheme.primaryContainer else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = null,
                                tint = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val BAR_HEIGHT = 60.dp
