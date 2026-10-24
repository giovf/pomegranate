package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.connection.ConnectionScreen
import com.gankdat.pomegranate.ui.coverage.CoverageScreen
import com.gankdat.pomegranate.ui.connection.DisconnectScreen
import com.gankdat.pomegranate.ui.guidance.CleanupGuidanceScreen
import com.gankdat.pomegranate.ui.settings.SettingsScreen
import com.gankdat.pomegranate.ui.library.CloudItemDetailRoute
import com.gankdat.pomegranate.ui.library.ItemDetailRoute
import com.gankdat.pomegranate.ui.library.LibraryScreen
import com.gankdat.pomegranate.ui.manifest.ManifestViewerScreen
import com.gankdat.pomegranate.ui.manifest.ProofOfCopyScreen
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
                PullScreen(
                    onDone = { navController.popBackStack() },
                    onViewProof = navController::navigateToProofOfCopy,
                    onAutoDisconnect = navController::navigateToDisconnect,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.Connection.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_connection)) { padding ->
                ConnectionScreen(
                    onDisconnect = navController::navigateToDisconnect,
                    onOpenCoverage = { navController.navigate(Screen.Coverage.route) { launchSingleTop = true } },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.Settings.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_settings)) { padding ->
                SettingsScreen(
                    onOpenConnection = navController::navigateToConnection,
                    onOpenProof = navController::navigateToProofOfCopy,
                    onOpenCleanupGuide = { navController.navigate(Screen.CleanupGuidance.route) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.Disconnect.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_disconnect)) { padding ->
                DisconnectScreen(
                    onFinished = navController::navigateToCleanupGuidanceAfterDisconnect,
                    onCancel = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.ProofOfCopy.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_proof)) { padding ->
                ProofOfCopyScreen(
                    onViewManifest = navController::navigateToManifestViewer,
                    onCleanupGuidance = { navController.navigate(Screen.CleanupGuidance.route) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.Coverage.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_coverage)) { padding ->
                CoverageScreen(modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.ManifestViewer.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_manifest)) { padding ->
                ManifestViewerScreen(modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.CleanupGuidance.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_cleanup_guidance)) { padding ->
                CleanupGuidanceScreen(onOpenProof = navController::navigateToProofOfCopy, modifier = Modifier.padding(padding))
            }
        }
    }
}

