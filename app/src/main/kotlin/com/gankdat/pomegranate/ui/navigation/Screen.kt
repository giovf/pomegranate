package com.gankdat.pomegranate.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.gankdat.pomegranate.data.model.PullScope

/**
 * Every destination in the app. Routes are plain strings so navigation needs
 * no serialization plugin; argument placeholders use Navigation's `{name}`
 * syntax and are resolved through the `createRoute` helpers.
 */
internal sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")

    /** Where your photos are and what to do next; the first screen after onboarding. */
    data object Home : Screen("home")

    /** The photo grid, shown as "Photos". */
    data object Library : Screen("library")

    /** Proof, connection, detailed views and settings. */
    data object More : Screen("more")

    data object ItemDetail : Screen("item/{$ARG_ITEM_ID}") {
        val arguments = listOf(navArgument(ARG_ITEM_ID) { type = NavType.LongType })

        fun createRoute(itemId: Long): String = "item/$itemId"
    }

    data object CloudItemDetail : Screen("cloud/{$ARG_CLOUD_ITEM_ID}") {
        val arguments = listOf(navArgument(ARG_CLOUD_ITEM_ID) { type = NavType.LongType })

        fun createRoute(cloudItemId: Long): String = "cloud/$cloudItemId"
    }

    /** Swipeable detail over the list held in the app's browse session, opened at [ARG_INDEX]. */
    data object Browse : Screen("browse/{$ARG_INDEX}") {
        val arguments = listOf(navArgument(ARG_INDEX) { type = NavType.IntType })

        fun createRoute(index: Int): String = "browse/$index"
    }

    data object Pull : Screen("pull/{$ARG_SCOPE}") {
        val arguments = listOf(navArgument(ARG_SCOPE) { type = NavType.StringType })

        fun createRoute(scope: PullScope): String = "pull/${scope.name}"

        /** Parses the scope argument, falling back to [PullScope.CLOUD_ONLY] for unknown values. */
        fun parseScope(raw: String?): PullScope = PullScope.entries.firstOrNull { it.name == raw } ?: PullScope.CLOUD_ONLY
    }

    data object Connection : Screen("connection")

    data object Settings : Screen("settings")

    data object Disconnect : Screen("disconnect")

    data object CleanupGuidance : Screen("cleanup")

    data object ProofOfCopy : Screen("proof")

    data object ManifestViewer : Screen("manifest")

    data object Coverage : Screen("coverage")

    data object ReviewMatches : Screen("review")

    companion object {
        const val ARG_ITEM_ID: String = "itemId"
        const val ARG_CLOUD_ITEM_ID: String = "cloudItemId"
        const val ARG_SCOPE: String = "scope"
        const val ARG_INDEX: String = "index"

        /**
         * Destinations that are entry points and show no back arrow. Lazy
         * because the subclasses are still initialising when the companion
         * runs; an eager set would capture nulls.
         */
        val roots: Set<Screen> by lazy { setOf(Onboarding, Home, Library, More, CleanupGuidance) }

        fun isRoot(route: String?): Boolean = roots.any { it.route == route }
    }
}
