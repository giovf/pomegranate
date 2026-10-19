package com.gankdat.pomegranate.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Which runtime permissions the census needs on a given Android version, and
 * how a grant result maps to a [PermissionState]. The decision logic is pure
 * so it can be unit tested without an Activity.
 */
internal object MediaPermissions {
    // Spelled out rather than taken from android.Manifest: the constants only
    // exist from API 33/34 and lint rejects inlining them below minSdk, but
    // the strings themselves are what the system dialog expects on any level.
    const val IMAGES: String = "android.permission.READ_MEDIA_IMAGES"
    const val VIDEO: String = "android.permission.READ_MEDIA_VIDEO"
    const val VISUAL_USER_SELECTED: String = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    /**
     * Android 11 and 12 predate the granular media permissions and would need
     * the legacy READ_EXTERNAL_STORAGE, which this app deliberately does not
     * declare. Requesting it is harmless (it is simply reported as denied).
     */
    const val LEGACY_READ: String = Manifest.permission.READ_EXTERNAL_STORAGE

    /** Permissions to request, in the order the system dialog should present them. */
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(IMAGES, VIDEO, VISUAL_USER_SELECTED)
        sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(IMAGES, VIDEO)
        else -> listOf(LEGACY_READ)
    }

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
        val fullAccess = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            granted[IMAGES] == true && granted[VIDEO] == true
        } else {
            granted[LEGACY_READ] == true
        }
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
