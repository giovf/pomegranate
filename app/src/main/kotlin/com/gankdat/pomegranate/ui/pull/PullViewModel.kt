package com.gankdat.pomegranate.ui.pull

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.TransferJobDao
import com.gankdat.pomegranate.data.db.entity.TransferJobEntity
import com.gankdat.pomegranate.data.db.entity.TransferState
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.data.settings.SettingsRepository
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.transfer.TransferEngine
import com.gankdat.pomegranate.transfer.TransferQueue
import com.gankdat.pomegranate.transfer.TransferScheduler
import com.gankdat.pomegranate.transfer.TransferSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the user can choose to pull. */
internal sealed interface ScopeChoice {
    data object CloudOnly : ScopeChoice

    data object Everything : ScopeChoice

    data class Origin(val label: String) : ScopeChoice
}

internal data class ScopeOption(val choice: ScopeChoice, val itemCount: Int)

/** One row of the "what's left" list. */
internal data class PendingJob(val job: TransferJobEntity, val filename: String, val liveBytes: Long)

internal data class PullProgress(
    val summary: TransferSummary = TransferSummary(),
    val pending: List<PendingJob> = emptyList(),
    val failed: List<PendingJob> = emptyList(),
    val bytesPerSecond: Double? = null,
    val etaSeconds: Long? = null,
) {
    val completed: Int get() = summary.done
    val total: Int get() = summary.total
    val isPaused: Boolean get() = summary.paused > 0 && summary.queued + summary.running + summary.verifying == 0
}

internal sealed interface PullPhase {
    data object ChooseScope : PullPhase

    data object InProgress : PullPhase

    data object Completed : PullPhase
}

internal class PullViewModel(
    private val cloudDao: CloudItemDao,
    private val jobDao: TransferJobDao,
    reconciliation: ReconciliationRepository,
    private val queue: TransferQueue,
    engine: TransferEngine,
    private val scheduler: TransferScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _selected = MutableStateFlow<ScopeChoice>(ScopeChoice.CloudOnly)
    val selected: StateFlow<ScopeChoice> = _selected.asStateFlow()

    private val _showFidelityNotice = MutableStateFlow(false)
    val showFidelityNotice: StateFlow<Boolean> = _showFidelityNotice.asStateFlow()

    private val speed = SpeedEstimator()
    private val speedState = MutableStateFlow<Pair<Double?, Long?>>(null to null)

    /** Cloud items not yet pulled (no DONE job), keyed by id, with their reconciled state. */
    private val candidates = combine(cloudDao.observeAll(), reconciliation.observeStates(), jobDao.observeAll()) { items, states, jobs ->
        val pulled = jobs.filter { it.state == TransferState.DONE }.map { it.cloudItemId }.toSet()
        items.filter { it.id !in pulled }.map { it to (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) }
    }

    val scopeOptions: StateFlow<List<ScopeOption>> = candidates.map { list ->
        val cloudOnly = list.filter { it.second == MediaState.CLOUD_ONLY }
        buildList {
            add(ScopeOption(ScopeChoice.CloudOnly, cloudOnly.size))
            add(ScopeOption(ScopeChoice.Everything, list.size))
            cloudOnly.groupBy { it.first.userOverrideOrigin ?: it.first.inferredOrigin }
                .mapNotNull { (origin, members) -> origin?.let { it to members } }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
                .forEach { (origin, members) -> add(ScopeOption(ScopeChoice.Origin(origin), members.size)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val progress: StateFlow<PullProgress> =
        combine(queue.observeSummary(), jobDao.observeAll(), cloudDao.observeAll(), engine.liveProgress, speedState) { summary, jobs, items, live, speedAndEta ->
            val names = items.associate { it.id to it.filename }
            fun row(job: TransferJobEntity) = PendingJob(job, names[job.cloudItemId] ?: "#${job.cloudItemId}", live[job.id] ?: job.bytesWritten)
            PullProgress(
                summary = summary,
                pending = jobs.filter { it.state != TransferState.DONE && it.state != TransferState.FAILED }.map(::row),
                failed = jobs.filter { it.state == TransferState.FAILED }.map(::row),
                bytesPerSecond = speedAndEta.first,
                etaSeconds = speedAndEta.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), PullProgress())

    private val _started = MutableStateFlow(false)
    val phase: StateFlow<PullPhase> = combine(progress, _started) { p, started ->
        when {
            p.summary.isActive || p.isPaused -> PullPhase.InProgress
            started || p.summary.done + p.summary.failed > 0 && p.total > 0 && !hasDismissedCompletion -> PullPhase.Completed
            else -> PullPhase.ChooseScope
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), PullPhase.ChooseScope)

    private var hasDismissedCompletion = false

    init {
        viewModelScope.launch {
            // Throughput sampling: once a second while anything is active.
            while (isActive) {
                delay(1_000)
                val p = progress.value
                if (p.summary.isActive) {
                    val inFlight = p.pending.sumOf { it.liveBytes }
                    speed.sample(System.currentTimeMillis(), p.summary.bytesWritten + inFlight)
                    val remaining = p.summary.queued + p.summary.running + p.summary.verifying
                    speedState.value = speed.bytesPerSecond() to speed.etaSeconds(remaining, p.summary.done, p.summary.bytesWritten, inFlight)
                } else if (speedState.value.first != null) {
                    speed.reset()
                    speedState.value = null to null
                }
            }
        }
    }

    fun select(choice: ScopeChoice) {
        _selected.value = choice
    }

    /** Shows the fidelity notice first unless the user opted out; then enqueues and starts. */
    fun requestStart() {
        viewModelScope.launch {
            if (settings.fidelityNoticeAcknowledged.first()) start() else _showFidelityNotice.value = true
        }
    }

    fun acknowledgeFidelityNotice(dontShowAgain: Boolean) {
        viewModelScope.launch {
            if (dontShowAgain) settings.setFidelityNoticeAcknowledged(true)
            _showFidelityNotice.value = false
            start()
        }
    }

    fun dismissFidelityNotice() {
        _showFidelityNotice.value = false
    }

    private suspend fun start() {
        val list = candidates.first()
        val ids = when (val choice = _selected.value) {
            ScopeChoice.CloudOnly -> list.filter { it.second == MediaState.CLOUD_ONLY }.map { it.first.id }
            ScopeChoice.Everything -> list.map { it.first.id }
            is ScopeChoice.Origin -> list.filter { it.second == MediaState.CLOUD_ONLY && (it.first.userOverrideOrigin ?: it.first.inferredOrigin) == choice.label }.map { it.first.id }
        }
        hasDismissedCompletion = false
        queue.enqueue(ids)
        scheduler.start(wifiOnly = settings.wifiOnly.first())
        _started.value = true
    }

    fun pause() {
        viewModelScope.launch {
            queue.pauseAll()
            scheduler.stop()
        }
    }

    fun resume() {
        viewModelScope.launch {
            queue.resumeAll()
            scheduler.start(wifiOnly = settings.wifiOnly.first())
        }
    }

    fun cancel() {
        viewModelScope.launch {
            scheduler.stop()
            queue.cancelAll()
            _started.value = false
            hasDismissedCompletion = true
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            queue.retryFailed()
            scheduler.start(wifiOnly = settings.wifiOnly.first())
            _started.value = true
        }
    }

    fun dismissCompletion() {
        hasDismissedCompletion = true
        _started.value = false
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
