package com.gankdat.pomegranate.ui.navigation

import androidx.navigation.NavController
import com.gankdat.pomegranate.data.model.PullScope

internal fun NavController.navigateToLibraryAfterOnboarding() {
    navigate(Screen.Library.route) {
        popUpTo(Screen.Onboarding.route) { inclusive = true }
        launchSingleTop = true
    }
}

internal fun NavController.navigateToItemDetail(itemId: Long) {
    navigate(Screen.ItemDetail.createRoute(itemId))
}

internal fun NavController.navigateToCloudItemDetail(cloudItemId: Long) {
    navigate(Screen.CloudItemDetail.createRoute(cloudItemId))
}

internal fun NavController.navigateToPull(scope: PullScope) {
    navigate(Screen.Pull.createRoute(scope))
}

internal fun NavController.navigateToConnection() {
    navigate(Screen.Connection.route) { launchSingleTop = true }
}

internal fun NavController.navigateToSettings() {
    navigate(Screen.Settings.route) { launchSingleTop = true }
}

internal fun NavController.navigateToDisconnect() {
    navigate(Screen.Disconnect.route) { launchSingleTop = true }
}

internal fun NavController.navigateToProofOfCopy() {
    navigate(Screen.ProofOfCopy.route) { launchSingleTop = true }
}

internal fun NavController.navigateToManifestViewer() {
    navigate(Screen.ManifestViewer.route) { launchSingleTop = true }
}

/** After a completed disconnect there is nothing to go back to; the whole stack is replaced. */
internal fun NavController.navigateToCleanupGuidanceAfterDisconnect() {
    navigate(Screen.CleanupGuidance.route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
