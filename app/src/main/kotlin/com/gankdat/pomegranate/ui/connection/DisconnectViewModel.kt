package com.gankdat.pomegranate.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.auth.AuthRepository
import com.gankdat.pomegranate.data.wipe.CloudDataWiper
import com.gankdat.pomegranate.data.wipe.WipeCounts
import com.gankdat.pomegranate.transfer.TransferQueue
import com.gankdat.pomegranate.transfer.TransferScheduler
import com.gankdat.pomegranate.transfer.TransferSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class DisconnectStep { REVOKE, WIPE_SECRETS, WIPE_CLOUD, CLEAR_CACHE }

internal sealed interface DisconnectPhase {
    /** Waiting for the user; [pending] is the live transfer summary. */
    data class PreCheck(val pending: TransferSummary) : DisconnectPhase

    data class Running(val current: DisconnectStep, val completed: Set<DisconnectStep>) : DisconnectPhase

    data class Complete(val counts: WipeCounts, val confirmedAtGoogle: Boolean) : DisconnectPhase
}

internal class DisconnectViewModel(
    private val auth: AuthRepository,
    private val queue: TransferQueue,
    private val scheduler: TransferScheduler,
    private val wiper: CloudDataWiper,
    private val clearThumbnails: () -> Unit,
) : ViewModel() {
    private val running = MutableStateFlow<DisconnectPhase?>(null)
    private val summary = queue.observeSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransferSummary())

    val phase: StateFlow<DisconnectPhase> = kotlinx.coroutines.flow.combine(running, summary) { r, s -> r ?: DisconnectPhase.PreCheck(s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DisconnectPhase.PreCheck(TransferSummary()))

    val hasPendingTransfers: Boolean get() = summary.value.let { it.isActive || it.paused > 0 }

    fun cancelPendingTransfers() {
        viewModelScope.launch {
            scheduler.stop()
            queue.cancelAll()
        }
    }

    /** Runs every step in order; each step is idempotent, so a retry after a crash is safe. */
    fun disconnect() {
        if (running.value is DisconnectPhase.Running) return
        viewModelScope.launch {
            val done = linkedSetOf<DisconnectStep>()
            fun step(s: DisconnectStep) { running.value = DisconnectPhase.Running(s, done.toSet()) }

            scheduler.cancelAll()
            queue.cancelAll()

            step(DisconnectStep.REVOKE)
            auth.revokeAndClear() // Revokes at Google (best effort) and wipes the token file and Keystore key.
            done += DisconnectStep.REVOKE
            step(DisconnectStep.WIPE_SECRETS)
            done += DisconnectStep.WIPE_SECRETS
            step(DisconnectStep.WIPE_CLOUD)
            val counts = wiper.wipe()
            done += DisconnectStep.WIPE_CLOUD
            step(DisconnectStep.CLEAR_CACHE)
            clearThumbnails()
            done += DisconnectStep.CLEAR_CACHE
            running.value = DisconnectPhase.Complete(counts, confirmedAtGoogle = false)
        }
    }

    fun setConfirmedAtGoogle(confirmed: Boolean) {
        running.update { (it as? DisconnectPhase.Complete)?.copy(confirmedAtGoogle = confirmed) ?: it }
    }
}
