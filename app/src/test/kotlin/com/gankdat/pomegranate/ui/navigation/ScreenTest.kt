package com.gankdat.pomegranate.ui.navigation

import com.gankdat.pomegranate.data.model.PullScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {
    private val allScreens = listOf(
        Screen.Onboarding,
        Screen.Library,
        Screen.ItemDetail,
        Screen.CloudItemDetail,
        Screen.Pull,
        Screen.Connection,
        Screen.Settings,
        Screen.Disconnect,
        Screen.CleanupGuidance,
        Screen.ProofOfCopy,
        Screen.ManifestViewer,
        Screen.Coverage,
    )

    @Test
    fun `routes are unique`() {
        assertEquals(allScreens.size, allScreens.map { it.route }.toSet().size)
    }

    @Test
    fun `item detail route carries the item id`() {
        assertEquals("item/42", Screen.ItemDetail.createRoute(42))
        assertTrue(Screen.ItemDetail.route.contains("{${Screen.ARG_ITEM_ID}}"))
    }

    @Test
    fun `cloud item detail route carries the id`() {
        assertEquals("cloud/7", Screen.CloudItemDetail.createRoute(7))
    }

    @Test
    fun `pull route round-trips every scope`() {
        PullScope.entries.forEach { scope ->
            val route = Screen.Pull.createRoute(scope)
            assertEquals(scope, Screen.Pull.parseScope(route.substringAfter("pull/")))
        }
    }

    @Test
    fun `unknown pull scope falls back to cloud-only`() {
        assertEquals(PullScope.CLOUD_ONLY, Screen.Pull.parseScope("EVERYTHING"))
        assertEquals(PullScope.CLOUD_ONLY, Screen.Pull.parseScope(null))
    }

    @Test
    fun `root screens show no back arrow and are real destinations`() {
        assertTrue(Screen.roots.all { it in allScreens })
        assertTrue(Screen.isRoot(Screen.Onboarding.route))
        assertTrue(Screen.isRoot(Screen.Library.route))
        assertFalse(Screen.isRoot(Screen.Settings.route))
        assertFalse(Screen.isRoot(null))
    }
}
