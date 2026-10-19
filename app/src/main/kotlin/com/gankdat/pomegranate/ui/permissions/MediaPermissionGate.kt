package com.gankdat.pomegranate.ui.permissions

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gankdat.pomegranate.ui.appViewModelFactory

/**
 * Wraps any screen that needs the device media library. Shows the rationale
 * or the Settings hand-off until access exists, then renders [content] with
 * the live [PermissionState] so it can degrade gracefully after "Not now".
 */
@Composable
internal fun MediaPermissionGate(
    modifier: Modifier = Modifier,
    viewModel: PermissionViewModel = viewModel(factory = appViewModelFactory { PermissionViewModel(it.settings) }),
    content: @Composable (PermissionState) -> Unit,
) {
    val activity = checkNotNull(LocalActivity.current) { "MediaPermissionGate must be hosted by an Activity" }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    var continueWithoutAccess by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult(activity)
    }
    val requestPermissions = { launcher.launch(MediaPermissions.required().toTypedArray()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(activity) }

    when (val permission = uiState.permission) {
        null -> Unit // First check in flight; render nothing rather than the wrong screen.
        PermissionState.NotRequested -> PermissionRationaleScreen(isRetry = false, onGrantAccess = requestPermissions, modifier = modifier)
        PermissionState.Denied -> PermissionRationaleScreen(isRetry = true, onGrantAccess = requestPermissions, modifier = modifier)
        PermissionState.PermanentlyDenied ->
            if (continueWithoutAccess) {
                content(permission)
            } else {
                PermissionDeniedScreen(onNotNow = { continueWithoutAccess = true }, modifier = modifier)
            }
        PermissionState.Granted -> content(permission)
        PermissionState.PartiallyGranted -> Column(modifier = modifier.fillMaxSize()) {
            if (!uiState.partialBannerDismissed) {
                PartialAccessBanner(
                    onSelectMore = requestPermissions,
                    onDismiss = viewModel::dismissPartialAccessBanner,
                )
            }
            content(permission)
        }
    }
}
