package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.connection.ConnectionScreen
import com.gankdat.pomegranate.ui.library.CloudItemDetailRoute
import com.gankdat.pomegranate.ui.library.ItemDetailRoute
import com.gankdat.pomegranate.ui.library.LibraryScreen
import com.gankdat.pomegranate.ui.onboarding.OnboardingScreen
import com.gankdat.pomegranate.ui.permissions.MediaPermissionGate
import com.gankdat.pomegranate.ui.pull.PullScreen
import com.gankdat.pomegranate.data.model.PullScope

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
                OnboardingScreen(onFinished = navController::navigateToLibraryAfterOnboarding, modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.Library.route) {
            PomegranateScaffold(
                navController,
                stringResource(R.string.title_library),
                actions = {
                    IconButton(onClick = { navController.navigateToPull(PullScope.CLOUD_ONLY) }) {
                        Icon(painter = painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.title_pull))
                    }
                    IconButton(onClick = navController::navigateToSettings) {
                        Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.title_settings))
                    }
                },
            ) { padding ->
                MediaPermissionGate(modifier = Modifier.padding(padding)) { permission ->
                    LibraryScreen(
                        permission = permission,
                        onOpenItem = { entry ->
                            entry.deviceItemId?.let(navController::navigateToItemDetail)
                                ?: entry.cloudItemId?.let(navController::navigateToCloudItemDetail)
                        },
                        onOpenConnection = navController::navigateToConnection,
                    )
                }
            }
        }
        composable(Screen.ItemDetail.route, arguments = Screen.ItemDetail.arguments) {
            ItemDetailRoute(navController)
        }
        composable(Screen.CloudItemDetail.route, arguments = Screen.CloudItemDetail.arguments) {
            CloudItemDetailRoute(navController)
        }
        composable(Screen.Pull.route, arguments = Screen.Pull.arguments) {
            PomegranateScaffold(navController, stringResource(R.string.title_pull)) { padding ->
                PullScreen(onDone = { navController.popBackStack() }, modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.Connection.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_connection)) { padding ->
                ConnectionScreen(modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.Settings.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_settings)) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    TextButton(onClick = navController::navigateToConnection) { Text(stringResource(R.string.title_connection)) }
                }
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
