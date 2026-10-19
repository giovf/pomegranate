package com.gankdat.pomegranate.ui.permissions

/** What the app may currently read from the device's media library. */
internal sealed interface PermissionState {
    /** The permission dialog has never been shown. */
    data object NotRequested : PermissionState

    /** Full read access to images and videos. */
    data object Granted : PermissionState

    /** Android 14+: the user shared a selection only; the census sees just those items. */
    data object PartiallyGranted : PermissionState

    /** Denied once; the system will still show the dialog if asked again. */
    data object Denied : PermissionState

    /** Denied with "don't ask again" (or via Settings); only Settings can change it. */
    data object PermanentlyDenied : PermissionState

    val allowsScanning: Boolean
        get() = this is Granted || this is PartiallyGranted
}
