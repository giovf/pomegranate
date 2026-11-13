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
import com.gankdat.pomegranate.ui.library.PullSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    /** The items ticked in the library. */
    data object Selected : ScopeChoice

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
    /** The pull these numbers describe; null when none is running or being looked at. */
    val batchId: Long? = null,
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
    private val pullSelection: PullSelection,
    /** Sessions of rounds Google has closed; their items cannot be downloaded until picked again. */
    closedSessions: Flow<Set<String>>,
) : ViewModel() {
    private val _selected = MutableStateFlow<ScopeChoice>(if (pullSelection.ids.value.isEmpty()) ScopeChoice.CloudOnly else ScopeChoice.Selected)
    val selected: StateFlow<ScopeChoice> = _selected.asStateFlow()

    private val _showFidelityNotice = MutableStateFlow(false)
    val showFidelityNotice: StateFlow<Boolean> = _showFidelityNotice.asStateFlow()

    private val speed = SpeedEstimator()
    private val speedState = MutableStateFlow<Pair<Double?, Long?>>(null to null)

    /** Cloud items not yet pulled (no DONE job), keyed by id, with their reconciled state. */
    private val notPulled = combine(cloudDao.observeAll(), reconciliation.observeStates(), jobDao.observeAll()) { items, states, jobs ->
        val pulled = jobs.filter { it.state == TransferState.DONE }.map { it.cloudItemId }.toSet()
        items.filter { it.id !in pulled }.map { it to (states.cloudStates[it.id] ?: MediaState.CLOUD_ONLY) }
    }

    private val candidates = combine(notPulled, closedSessions) { list, closed -> list.filter { it.first.sessionId !in closed } }

    /** Items only in Google Photos that cannot be pulled because their round is closed. */
    val closedCount: StateFlow<Int> = combine(notPulled, closedSessions) { list, closed ->
        list.count { it.first.sessionId in closed && it.second == MediaState.CLOUD_ONLY }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)

    val scopeOptions: StateFlow<List<ScopeOption>> = combine(candidates, pullSelection.ids) { list, ticked ->
        val cloudOnly = list.filter { it.second == MediaState.CLOUD_ONLY }
        buildList {
            if (ticked.isNotEmpty()) add(ScopeOption(ScopeChoice.Selected, list.count { it.first.id in ticked }))
            add(ScopeOption(ScopeChoice.CloudOnly, cloudOnly.size))
            add(ScopeOption(ScopeChoice.Everything, list.size))
            cloudOnly.groupBy { it.first.userOverrideOrigin ?: it.first.inferredOrigin }
                .mapNotNull { (origin, members) -> origin?.let { it to members } }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })
                .forEach { (origin, members) -> add(ScopeOption(ScopeChoice.Origin(origin), members.size)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** The finished pull whose summary is open; null shows the "choose what to pull" page. */
    private val _summaryBatch = MutableStateFlow<Long?>(null)

    private val _justFinished = MutableStateFlow(false)

    /** True while the open summary is the pull that has just run, as opposed to one reopened from its card. */
    val justFinished: StateFlow<Boolean> = _justFinished.asStateFlow()

    /** Numbers for one pull only: the one running, else the one whose summary is open. Earlier pulls never leak in. */
    val progress: StateFlow<PullProgress> =
        combine(_summaryBatch, jobDao.observeAll(), cloudDao.observeAll(), engine.liveProgress, speedState) { shown, jobs, items, live, speedAndEta ->
            val batch = PullBatches.activeBatch(jobs) ?: shown
            val mine = if (batch == null) emptyList() else jobs.filter { it.batchId == batch }
            val names = items.associate { it.id to it.filename }
            fun row(job: TransferJobEntity) = PendingJob(job, names[job.cloudItemId] ?: job.originalFilename.ifBlank { "#${job.cloudItemId}" }, live[job.id] ?: job.bytesWritten)
            PullProgress(
                summary = TransferSummary.of(mine.map { it.state to (it.bytesWritten to it.bytesExpected) }),
                pending = mine.filter { it.state != TransferState.DONE && it.state != TransferState.FAILED }.map(::row),
                failed = mine.filter { it.state == TransferState.FAILED }.map(::row),
                bytesPerSecond = speedAndEta.first,
                etaSeconds = speedAndEta.second,
                batchId = batch,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), PullProgress())

    /** Finished pulls, newest first, for the cards under the options. */
    val history: StateFlow<List<PullBatch>> = combine(jobDao.observeAll(), settings.cleanupConfirmedAtMillis) { jobs, confirmedAt ->
        PullBatches.history(jobs, confirmedAt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    /** Mirrors the setting so the completion screen can start its countdown. */
    val autoDisconnect: StateFlow<Boolean> = settings.autoDisconnect.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    /** A summary is shown only for a pull the user just ran or chose to look at; otherwise a new pull can always be started. */
    val phase: StateFlow<PullPhase> = combine(progress, _summaryBatch) { p, shown ->
        when {
            p.summary.isActive || p.isPaused -> PullPhase.InProgress
            shown != null -> PullPhase.Completed
            else -> PullPhase.ChooseScope
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), PullPhase.ChooseScope)

    /** Opens the summary of an earlier pull from its card. */
    fun openSummary(batchId: Long) {
        _justFinished.value = false
        _summaryBatch.value = batchId
    }

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
        val ticked = pullSelection.ids.value
        val ids = when (val choice = _selected.value) {
            ScopeChoice.Selected -> list.filter { it.first.id in ticked }.map { it.first.id }
            ScopeChoice.CloudOnly -> list.filter { it.second == MediaState.CLOUD_ONLY }.map { it.first.id }
            ScopeChoice.Everything -> list.map { it.first.id }
            is ScopeChoice.Origin -> list.filter { it.second == MediaState.CLOUD_ONLY && (it.first.userOverrideOrigin ?: it.first.inferredOrigin) == choice.label }.map { it.first.id }
        }
        if (queue.enqueue(ids) == 0) return
        pullSelection.clear()
        _justFinished.value = true
        _summaryBatch.value = jobDao.getAll().maxOfOrNull { it.batchId }
        scheduler.start(wifiOnly = settings.wifiOnly.first(), pauseOnLowBattery = settings.pauseOnLowBattery.first())
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
            scheduler.start(wifiOnly = settings.wifiOnly.first(), pauseOnLowBattery = settings.pauseOnLowBattery.first())
        }
    }

    fun cancel() {
        viewModelScope.launch {
            scheduler.stop()
            queue.cancelAll()
            dismissCompletion()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            val retried = jobDao.getByState(TransferState.FAILED).maxOfOrNull { it.batchId }
            queue.retryFailed()
            scheduler.start(wifiOnly = settings.wifiOnly.first(), pauseOnLowBattery = settings.pauseOnLowBattery.first())
            if (retried != null) _summaryBatch.value = retried
        }
    }

    /** Closes the open summary; the pull stays listed as a card. */
    fun dismissCompletion() {
        _justFinished.value = false
        _summaryBatch.value = null
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
