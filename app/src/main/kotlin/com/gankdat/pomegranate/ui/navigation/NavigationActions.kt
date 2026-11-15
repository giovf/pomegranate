package com.gankdat.pomegranate.ui.navigation

import androidx.navigation.NavController
import com.gankdat.pomegranate.data.model.PullScope

internal fun NavController.navigateToHomeAfterOnboarding() {
    navigate(Screen.Home.route) {
        popUpTo(Screen.Onboarding.route) { inclusive = true }
        launchSingleTop = true
    }
}

/** Switches between the main screens; Back from Photos or More returns to Home, and each keeps its scroll position. */
internal fun NavController.navigateToTab(tab: BottomTab) {
    navigate(tab.screen.route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavController.navigateToItemDetail(itemId: Long) {
    navigate(Screen.ItemDetail.createRoute(itemId))
}

internal fun NavController.navigateToCloudItemDetail(cloudItemId: Long) {
    navigate(Screen.CloudItemDetail.createRoute(cloudItemId))
}

internal fun NavController.navigateToBrowse(index: Int) {
    navigate(Screen.Browse.createRoute(index))
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
