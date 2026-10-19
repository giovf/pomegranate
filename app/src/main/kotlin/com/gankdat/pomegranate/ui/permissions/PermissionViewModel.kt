package com.gankdat.pomegranate.ui.permissions

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PermissionUiState(
    /** Null until the first check completes, so the UI can avoid flashing the wrong screen. */
    val permission: PermissionState? = null,
    val partialBannerDismissed: Boolean = false,
)

internal class PermissionViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(PermissionUiState())
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.partialAccessBannerDismissed.collect { dismissed ->
                _state.update { it.copy(partialBannerDismissed = dismissed) }
            }
        }
    }

    /** Re-reads the system grant state; called on every resume so changes made in Settings are picked up. */
    fun refresh(activity: Activity) {
        viewModelScope.launch {
            val requestedBefore = settings.hasRequestedMediaPermission.first()
            _state.update { it.copy(permission = MediaPermissions.check(activity, requestedBefore)) }
        }
    }

    /** Record that the system dialog has been shown, then re-evaluate with the result. */
    fun onPermissionResult(activity: Activity) {
        viewModelScope.launch {
            settings.setHasRequestedMediaPermission(true)
            _state.update { it.copy(permission = MediaPermissions.check(activity, requestedBefore = true)) }
        }
    }

    fun dismissPartialAccessBanner() {
        viewModelScope.launch { settings.setPartialAccessBannerDismissed(true) }
    }
}
