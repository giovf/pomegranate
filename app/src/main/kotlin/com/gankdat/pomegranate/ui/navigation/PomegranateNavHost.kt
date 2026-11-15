package com.gankdat.pomegranate.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.connection.ConnectionScreen
import com.gankdat.pomegranate.ui.coverage.CoverageScreen
import com.gankdat.pomegranate.ui.connection.DisconnectScreen
import com.gankdat.pomegranate.ui.guidance.CleanupGuidanceScreen
import com.gankdat.pomegranate.ui.home.HomeScreen
import com.gankdat.pomegranate.ui.more.MoreScreen
import com.gankdat.pomegranate.ui.settings.SettingsScreen
import com.gankdat.pomegranate.ui.library.CloudItemDetailRoute
import com.gankdat.pomegranate.ui.library.ItemDetailRoute
import com.gankdat.pomegranate.ui.library.LibraryScreen
import com.gankdat.pomegranate.ui.library.MediaPagerRoute
import com.gankdat.pomegranate.ui.manifest.ManifestViewerScreen
import com.gankdat.pomegranate.ui.manifest.ProofOfCopyScreen
import com.gankdat.pomegranate.ui.onboarding.OnboardingScreen
import com.gankdat.pomegranate.ui.permissions.MediaPermissionGate
import com.gankdat.pomegranate.ui.pull.PullScreen
import com.gankdat.pomegranate.ui.review.ReviewMatchesScreen
import com.gankdat.pomegranate.data.model.PullScope

/** The navigation graph: three main screens behind the bottom bar, and everything reached from them. */
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
                OnboardingScreen(onFinished = navController::navigateToHomeAfterOnboarding, modifier = Modifier.padding(padding))
            }
        }
        composable(Screen.Home.route) {
            PomegranateScaffold(navController, stringResource(R.string.tab_home), showTopBar = false, bottomTab = BottomTab.HOME) { padding ->
                MediaPermissionGate(modifier = Modifier.padding(padding)) { permission ->
                    HomeScreen(
                        permission = permission,
                        onOpenGooglePhotos = navController::navigateToConnection,
                        onCopy = { navController.navigateToPull(PullScope.CLOUD_ONLY) },
                        onOpenProof = navController::navigateToProofOfCopy,
                    )
                }
            }
        }
        composable(Screen.More.route) {
            PomegranateScaffold(navController, stringResource(R.string.tab_more), bottomTab = BottomTab.MORE) { padding ->
                MoreScreen(
                    onOpenProof = navController::navigateToProofOfCopy,
                    onOpenConnection = navController::navigateToConnection,
                    onOpenCleanupGuide = { navController.navigate(Screen.CleanupGuidance.route) },
                    onOpenCoverage = { navController.navigate(Screen.Coverage.route) { launchSingleTop = true } },
                    onOpenReview = { navController.navigate(Screen.ReviewMatches.route) { launchSingleTop = true } },
                    onOpenSettings = navController::navigateToSettings,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.Library.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_library), bottomTab = BottomTab.PHOTOS) { padding ->
                MediaPermissionGate(modifier = Modifier.padding(padding)) { permission ->
                    LibraryScreen(
                        permission = permission,
                        onOpenItem = navController::navigateToBrowse,
                        onPullSelected = { navController.navigateToPull(PullScope.SELECTED) },
                        onOpenConnection = navController::navigateToConnection,
                    )
                }
            }
        }
        composable(Screen.ItemDetail.route, arguments = Screen.ItemDetail.arguments) {
            ItemDetailRoute(navController)
        }
        composable(Screen.Browse.route, arguments = Screen.Browse.arguments) { entry ->
            MediaPagerRoute(navController, startIndex = entry.arguments?.getInt(Screen.ARG_INDEX) ?: 0)
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
                    onPull = { navController.navigateToPull(PullScope.SELECTED) },
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
                CoverageScreen(
                    onReviewMatches = { navController.navigate(Screen.ReviewMatches.route) { launchSingleTop = true } },
                    modifier = Modifier.padding(padding),
                )
            }
        }
        composable(Screen.ReviewMatches.route) {
            PomegranateScaffold(navController, stringResource(R.string.title_review_matches)) { padding ->
                ReviewMatchesScreen(
                    onOpenDeviceItem = navController::navigateToItemDetail,
                    onOpenCloudItem = navController::navigateToCloudItemDetail,
                    modifier = Modifier.padding(padding),
                )
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

