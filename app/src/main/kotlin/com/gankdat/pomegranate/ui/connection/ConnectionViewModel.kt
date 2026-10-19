package com.gankdat.pomegranate.ui.connection

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.PickerApiException
import com.gankdat.pomegranate.census.cloud.PickerAuthException
import com.gankdat.pomegranate.census.cloud.SessionState
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface RoundState {
    data object Idle : RoundState

    data object Starting : RoundState

    data class Picking(val sessionId: String, val pickerUri: String) : RoundState

    data object Indexing : RoundState

    data class Done(val itemCount: Int) : RoundState

    data class Failed(val message: String) : RoundState
}

internal data class CoverageUi(val itemCount: Int = 0, val roundCount: Int = 0, val lastRoundAt: Long? = null)

internal class ConnectionViewModel(
    private val auth: AuthRepository,
    private val cloud: CloudScannerRepository,
    private val reconciliation: ReconciliationRepository,
) : ViewModel() {
    val authState = auth.authState

    private val _roundState = MutableStateFlow<RoundState>(RoundState.Idle)
    val roundState: StateFlow<RoundState> = _roundState.asStateFlow()
    private var pollJob: Job? = null

    val coverage: StateFlow<CoverageUi> = combine(cloud.coverage(), cloud.pickerRounds()) { coverage, rounds ->
        CoverageUi(
            itemCount = coverage.itemCount,
            roundCount = coverage.roundCount,
            lastRoundAt = rounds.firstOrNull { it.state == PickerRoundState.INDEXED }?.createdAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUi())

    fun signIn(activity: Activity) = auth.launchSignIn(activity)

    fun signOut() {
        cancelRound()
        viewModelScope.launch { auth.revokeAndClear() }
    }

    fun dismissError() = auth.dismissError()

    /** Creates a session and opens Google's picker; polling starts right away and continues across the tab switch. */
    fun startRound(openPicker: (String) -> Unit) {
        if (_roundState.value is RoundState.Starting || _roundState.value is RoundState.Picking) return
        _roundState.value = RoundState.Starting
        viewModelScope.launch {
            val session = try {
                cloud.startPickerRound()
            } catch (e: PickerApiException) {
                _roundState.value = RoundState.Failed(if (e.isAuthFailure) "Google sign-in is no longer valid" else "Google Photos returned ${e.httpStatus}")
                return@launch
            } catch (_: PickerAuthException) {
                _roundState.value = RoundState.Failed("Please sign in again")
                return@launch
            } catch (_: IOException) {
                _roundState.value = RoundState.Failed("Could not reach Google Photos")
                return@launch
            }
            _roundState.value = RoundState.Picking(session.id, session.pickerUri)
            openPicker(session.pickerUri)
            pollAndIndex(session.id)
        }
    }

    fun resumeRoundIfPicking() {
        val picking = _roundState.value as? RoundState.Picking ?: return
        if (pollJob?.isActive != true) pollAndIndex(picking.sessionId)
    }

    fun cancelRound() {
        pollJob?.cancel()
        pollJob = null
        val picking = _roundState.value as? RoundState.Picking
        _roundState.value = RoundState.Idle
        if (picking != null) {
            viewModelScope.launch {
                try {
                    cloud.deleteSession(picking.sessionId)
                } catch (_: PickerApiException) {
                    // Nothing to clean up locally; Google will expire it.
                } catch (_: PickerAuthException) {
                    // Signed out meanwhile.
                } catch (_: IOException) {
                    // Offline; Google will expire it.
                }
            }
        }
    }

    fun dismissRoundResult() {
        if (_roundState.value is RoundState.Done || _roundState.value is RoundState.Failed) _roundState.value = RoundState.Idle
    }

    private fun pollAndIndex(sessionId: String) {
        pollJob = viewModelScope.launch {
            when (val state = cloud.pollSession(sessionId)) {
                SessionState.Complete -> {
                    _roundState.value = RoundState.Indexing
                    try {
                        val count = cloud.indexSession(sessionId)
                        reconciliation.reconcile()
                        _roundState.value = RoundState.Done(count)
                    } catch (e: PickerApiException) {
                        _roundState.value = RoundState.Failed("Google Photos returned ${e.httpStatus} while listing your selection")
                    } catch (_: PickerAuthException) {
                        _roundState.value = RoundState.Failed("Please sign in again")
                    } catch (_: IOException) {
                        _roundState.value = RoundState.Failed("Could not reach Google Photos while listing your selection")
                    }
                }
                SessionState.Timeout -> _roundState.value = RoundState.Failed("The picker session timed out before you finished selecting")
                is SessionState.Error -> _roundState.value = RoundState.Failed(state.message)
                SessionState.Selecting -> Unit
            }
        }
    }
}
