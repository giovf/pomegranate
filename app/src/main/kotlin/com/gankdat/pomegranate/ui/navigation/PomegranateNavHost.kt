package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gankdat.pomegranate.R

/**
 * The navigation graph. Each destination currently renders a placeholder
 * inside the standard scaffold; feature tasks replace the placeholders
 * without touching routes or arguments.
 */
@Composable
internal fun PomegranateNavHost(
    navController: NavHostController,
    startDestination: Screen,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(Screen.Onboarding.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_onboarding), showTopBar = false) { padding ->
                Placeholder(stringResource(R.string.title_onboarding), padding)
            }
        }
        composable(Screen.Library.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_library)) { padding ->
                Placeholder(stringResource(R.string.title_library), padding)
            }
        }
        composable(Screen.ItemDetail.route, arguments = Screen.ItemDetail.arguments) { entry ->
            val itemId = entry.arguments?.getLong(Screen.ARG_ITEM_ID) ?: 0L
            PomegranateScaffold(navController, stringResource(R.string.title_item_detail)) { padding ->
                Placeholder(stringResource(R.string.placeholder_item_detail, itemId), padding)
            }
        }
        composable(Screen.Pull.route, arguments = Screen.Pull.arguments) { entry ->
            val scope = Screen.Pull.parseScope(entry.arguments?.getString(Screen.ARG_SCOPE))
            PomegranateScaffold(navController, stringResource(R.string.title_pull)) { padding ->
                Placeholder(stringResource(R.string.placeholder_pull, scope.name), padding)
            }
        }
        composable(Screen.Connection.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_connection)) { padding ->
                Placeholder(stringResource(R.string.title_connection), padding)
            }
        }
        composable(Screen.Settings.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_settings)) { padding ->
                Placeholder(stringResource(R.string.title_settings), padding)
            }
        }
        composable(Screen.Disconnect.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_disconnect)) { padding ->
                Placeholder(stringResource(R.string.title_disconnect), padding)
            }
        }
        composable(Screen.CleanupGuidance.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_cleanup_guidance)) { padding ->
                Placeholder(stringResource(R.string.title_cleanup_guidance), padding)
            }
        }
    }
}

@Composable
private fun Placeholder(label: String, padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
