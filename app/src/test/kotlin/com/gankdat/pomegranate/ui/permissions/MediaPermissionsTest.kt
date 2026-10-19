package com.gankdat.pomegranate.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionsTest {
    private val api33 = 33
    private val api34 = 34

    @Test
    fun `required permissions follow the API level`() {
        assertEquals(listOf(MediaPermissions.IMAGES, MediaPermissions.VIDEO), MediaPermissions.required(api33))
        assertEquals(
            listOf(MediaPermissions.IMAGES, MediaPermissions.VIDEO, MediaPermissions.VISUAL_USER_SELECTED),
            MediaPermissions.required(api34),
        )
    }

    @Test
    fun `both media permissions granted means full access`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(MediaPermissions.IMAGES to true, MediaPermissions.VIDEO to true),
            sdkInt = api33,
            shouldShowRationale = false,
            requestedBefore = true,
        )
        assertEquals(PermissionState.Granted, state)
        assertTrue(state.allowsScanning)
    }

    @Test
    fun `android 14 selection-only grant is partial access`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(
                MediaPermissions.IMAGES to false,
                MediaPermissions.VIDEO to false,
                MediaPermissions.VISUAL_USER_SELECTED to true,
            ),
            sdkInt = api34,
            shouldShowRationale = false,
            requestedBefore = true,
        )
        assertEquals(PermissionState.PartiallyGranted, state)
        assertTrue(state.allowsScanning)
    }

    @Test
    fun `android 14 full grant also reports visual user selected and is still full access`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(
                MediaPermissions.IMAGES to true,
                MediaPermissions.VIDEO to true,
                MediaPermissions.VISUAL_USER_SELECTED to true,
            ),
            sdkInt = api34,
            shouldShowRationale = false,
            requestedBefore = true,
        )
        assertEquals(PermissionState.Granted, state)
    }

    @Test
    fun `only one of images or video granted is not full access`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(MediaPermissions.IMAGES to true, MediaPermissions.VIDEO to false),
            sdkInt = api33,
            shouldShowRationale = true,
            requestedBefore = true,
        )
        assertEquals(PermissionState.Denied, state)
        assertFalse(state.allowsScanning)
    }

    @Test
    fun `never asked and no rationale is NotRequested`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(MediaPermissions.IMAGES to false, MediaPermissions.VIDEO to false),
            sdkInt = api33,
            shouldShowRationale = false,
            requestedBefore = false,
        )
        assertEquals(PermissionState.NotRequested, state)
    }

    @Test
    fun `denied once with rationale available can be asked again`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(MediaPermissions.IMAGES to false, MediaPermissions.VIDEO to false),
            sdkInt = api33,
            shouldShowRationale = true,
            requestedBefore = true,
        )
        assertEquals(PermissionState.Denied, state)
    }

    @Test
    fun `asked before with no rationale is permanent denial`() {
        val state = MediaPermissions.resolve(
            granted = mapOf(MediaPermissions.IMAGES to false, MediaPermissions.VIDEO to false),
            sdkInt = api33,
            shouldShowRationale = false,
            requestedBefore = true,
        )
        assertEquals(PermissionState.PermanentlyDenied, state)
        assertFalse(state.allowsScanning)
    }

}
