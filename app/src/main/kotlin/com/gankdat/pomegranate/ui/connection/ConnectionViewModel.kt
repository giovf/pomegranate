package com.gankdat.pomegranate.ui.connection

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.auth.AuthState
import com.gankdat.pomegranate.auth.TokenStatus
import com.gankdat.pomegranate.census.cloud.CloudScannerRepository
import com.gankdat.pomegranate.census.cloud.PickerApiException
import com.gankdat.pomegranate.census.cloud.PickerAuthException
import com.gankdat.pomegranate.census.cloud.PickerSession
import com.gankdat.pomegranate.census.cloud.RoundAccounting
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.census.cloud.SessionState
import com.gankdat.pomegranate.data.db.entity.PickerRoundEntity
import com.gankdat.pomegranate.data.db.entity.PickerRoundState
import com.gankdat.pomegranate.data.db.entity.SessionRange
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.reconcile.origin.OriginRepository
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface RoundState {
    data object Idle : RoundState

    data object Starting : RoundState

    /** Waiting for the user to press Done in Google's picker; [expiresAt] is when Google closes the round by itself. */
    data class Picking(val sessionId: String, val pickerUri: String, val expiresAt: Long?) : RoundState

    data object Indexing : RoundState

    /** Just finished; shown above the "start next round" choice. */
    data class Done(val itemCount: Int, val oldest: Long?, val newest: Long?) : RoundState

    data class Failed(val message: String) : RoundState
}

/**
 * One round, with the date span of the photos picked in it. [selected] is how
 * many items the round listed; [alreadyIndexed] how many of them an earlier
 * round had already indexed (null when it cannot be worked out).
 */
internal data class RoundSummary(val round: PickerRoundEntity, val range: SessionRange?, val selected: Int, val alreadyIndexed: Int?)

internal data class CoverageUi(
    val itemCount: Int = 0,
    val roundCount: Int = 0,
    /** Date span of everything indexed so far. */
    val oldest: Long? = null,
    val newest: Long? = null,
    val lastRound: RoundSummary? = null,
)

internal class ConnectionViewModel(
    private val auth: AuthRepository,
    private val cloud: CloudScannerRepository,
    private val reconciliation: ReconciliationRepository,
    private val origins: OriginRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val authState = auth.authState

    val tokenStatus: StateFlow<TokenStatus?> = auth.tokenStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Newest first, each with its photos' date span. */
    val rounds: StateFlow<List<RoundSummary>> = combine(cloud.pickerRounds(), cloud.sessionRanges(), settings.roundOverlaps) { rounds, ranges, recorded ->
        val bySession = ranges.associateBy { it.sessionId }
        val indexedOldestFirst = rounds.filter { it.state == PickerRoundState.INDEXED }.sortedBy { it.createdAt }
        val overlaps = RoundAccounting.alreadyIndexed(
            indexedOldestFirst.map { RoundAccounting.Round(it.sessionId, it.itemCount) },
            recorded,
            totalIndexed = ranges.sumOf { it.itemCount },
        )
        rounds.map { RoundSummary(it, bySession[it.sessionId], selected = it.itemCount, alreadyIndexed = overlaps[it.sessionId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val coverage: StateFlow<CoverageUi> = combine(cloud.coverage(), rounds) { coverage, summaries ->
        val ranges = summaries.mapNotNull { it.range }
        CoverageUi(
            itemCount = coverage.itemCount,
            roundCount = coverage.roundCount,
            oldest = ranges.mapNotNull { it.oldest }.minOrNull(),
            newest = ranges.mapNotNull { it.newest }.maxOrNull(),
            lastRound = summaries.firstOrNull { it.round.state == PickerRoundState.INDEXED },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUi())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _roundState = MutableStateFlow<RoundState>(RoundState.Idle)
    val roundState: StateFlow<RoundState> = _roundState.asStateFlow()
    private var pollJob: Job? = null

    init {
        // A round left open (screen closed, app restarted) picks up where it was.
        viewModelScope.launch {
            if (auth.authState.first { it != AuthState.SigningIn } != AuthState.SignedIn) return@launch
            val open = try {
                cloud.openRound()
            } catch (_: PickerAuthException) {
                null
            } catch (_: IOException) {
                null
            } ?: return@launch
            if (_roundState.value == RoundState.Idle) {
                _roundState.value = open.toPicking()
                pollAndIndex(open.id)
            }
        }
    }

    fun refreshAccess() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                auth.refreshToken()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun signIn(activity: Activity) = auth.launchSignIn(activity)

    /** Cancels an open round only; the full revoke-and-wipe lives in the Disconnect flow. */
    fun abandonSignIn() {
        cancelRound()
        viewModelScope.launch { auth.revokeAndClear() }
    }

    fun dismissError() = auth.dismissError()

    /** Creates a round and hands its link to [openPicker]; polling starts right away and continues while the user picks elsewhere. */
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
            _roundState.value = session.toPicking()
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

    private fun PickerSession.toPicking() = RoundState.Picking(id, pickerUri, expireTimeMillis)

    private fun pollAndIndex(sessionId: String) {
        pollJob = viewModelScope.launch {
            when (val state = cloud.pollSession(sessionId)) {
                SessionState.Complete -> {
                    _roundState.value = RoundState.Indexing
                    try {
                        val count = cloud.indexSession(sessionId)
                        reconciliation.reconcile()
                        origins.refresh()
                        val range = cloud.sessionRanges().first().firstOrNull { it.sessionId == sessionId }
                        _roundState.value = RoundState.Done(count, range?.oldest, range?.newest)
                    } catch (e: PickerApiException) {
                        _roundState.value = RoundState.Failed("Google Photos returned ${e.httpStatus} while listing your selection")
                    } catch (_: PickerAuthException) {
                        _roundState.value = RoundState.Failed("Please sign in again")
                    } catch (_: IOException) {
                        _roundState.value = RoundState.Failed("Could not reach Google Photos while listing your selection")
                    }
                }
                SessionState.Timeout -> _roundState.value = RoundState.Failed("This round closed before Done was pressed, so nothing was added. Start a new round.")
                is SessionState.Error -> _roundState.value = RoundState.Failed(state.message)
                SessionState.Selecting -> Unit
            }
        }
    }
}
