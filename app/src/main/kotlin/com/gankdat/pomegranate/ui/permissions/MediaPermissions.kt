package com.gankdat.pomegranate.ui.permissions

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Which runtime permissions the census needs on a given Android version, and
 * how a grant result maps to a [PermissionState]. The decision logic is pure
 * so it can be unit tested without an Activity. minSdk is 33, so the
 * granular media permissions always apply.
 */
internal object MediaPermissions {
    // Spelled out rather than taken from android.Manifest: the API 34 constant
    // would be inlined below minSdk and lint rejects that, but the strings are
    // exactly what the system dialog expects.
    const val IMAGES: String = "android.permission.READ_MEDIA_IMAGES"
    const val VIDEO: String = "android.permission.READ_MEDIA_VIDEO"
    const val VISUAL_USER_SELECTED: String = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    /** Permissions to request, in the order the system dialog should present them. */
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) listOf(IMAGES, VIDEO, VISUAL_USER_SELECTED) else listOf(IMAGES, VIDEO)

    /**
     * Maps the current grants to a state.
     *
     * [shouldShowRationale] is false both before the first request and after a
     * permanent denial, so [requestedBefore] (persisted by the app) is what
     * tells those two apart.
     */
    fun resolve(
        granted: Map<String, Boolean>,
        sdkInt: Int,
        shouldShowRationale: Boolean,
        requestedBefore: Boolean,
    ): PermissionState {
        val fullAccess = granted[IMAGES] == true && granted[VIDEO] == true
        return when {
            fullAccess -> PermissionState.Granted
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted[VISUAL_USER_SELECTED] == true -> PermissionState.PartiallyGranted
            shouldShowRationale -> PermissionState.Denied
            requestedBefore -> PermissionState.PermanentlyDenied
            else -> PermissionState.NotRequested
        }
    }

    /** Reads the live grant state from the system for [activity]. */
    fun check(activity: Activity, requestedBefore: Boolean): PermissionState {
        val required = required()
        val granted = required.associateWith {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
        val rationale = required.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        return resolve(granted, Build.VERSION.SDK_INT, rationale, requestedBefore)
    }
}
