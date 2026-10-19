package com.gankdat.pomegranate.ui.onboarding

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OnboardingUiState(
    val destinationUri: String? = null,
    val destination: DestinationInfo? = null,
    /** Set when the chosen folder looks like one Google Photos backs up; cleared when acknowledged. */
    val showBackupWarning: Boolean = false,
    val error: OnboardingError? = null,
) {
    val isDestinationSelected: Boolean get() = destinationUri != null
}

internal enum class OnboardingError { PERMISSION_NOT_PERSISTABLE, DESTINATION_REVOKED }

internal class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val access: DestinationAccess,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settings.destinationRootUri.first() ?: return@launch
            val uri = stored.toUri()
            if (access.isPersisted(uri)) {
                _state.update { it.copy(destinationUri = stored, destination = describe(uri)) }
            } else {
                // The grant was revoked (app data cleared, folder deleted, card removed); forget it.
                settings.setDestinationRootUri(null)
                _state.update { it.copy(error = OnboardingError.DESTINATION_REVOKED) }
            }
        }
    }

    /** Called with the tree URI from the system folder picker. */
    fun onDestinationPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                access.takePersistable(uri)
            } catch (_: SecurityException) {
                _state.update { it.copy(error = OnboardingError.PERMISSION_NOT_PERSISTABLE) }
                return@launch
            }
            settings.setDestinationRootUri(uri.toString())
            val info = describe(uri)
            _state.update { it.copy(destinationUri = uri.toString(), destination = info, showBackupWarning = info?.isBackupFolder == true, error = null) }
        }
    }

    fun acknowledgeBackupWarning() {
        _state.update { it.copy(showBackupWarning = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    private fun describe(uri: Uri): DestinationInfo? = try {
        DestinationFolders.describe(access.treeDocumentId(uri))
    } catch (_: IllegalArgumentException) {
        null
    }
}
