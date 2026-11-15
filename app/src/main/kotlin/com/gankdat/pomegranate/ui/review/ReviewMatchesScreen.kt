package com.gankdat.pomegranate.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.gankdat.pomegranate.ui.components.MascotPanel
import com.gankdat.pomegranate.ui.components.PrimaryButton
import com.gankdat.pomegranate.ui.components.SecondaryButton
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.entity.CloudItemEntity
import com.gankdat.pomegranate.data.db.entity.DeviceItemEntity
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.library.MediaThumbnail
import com.gankdat.pomegranate.ui.library.ThumbnailSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One uncertain pair: the phone file and the Google item the matcher could not decide on. */
internal data class ReviewPair(val device: DeviceItemEntity, val cloud: CloudItemEntity)

/** What the screen shows: the pair to decide now, and how many are left including it. */
internal data class ReviewState(val current: ReviewPair?, val remaining: Int, val skipped: Int)

/**
 * Walks the user through ambiguous matches one at a time. Same and Different
 * are remembered through [ReconciliationRepository.decide]; Skip only moves
 * on for this visit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ReviewMatchesViewModel(
    private val reconciliation: ReconciliationRepository,
    deviceDao: DeviceItemDao,
    cloudDao: CloudItemDao,
) : ViewModel() {
    private val skipped = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())

    private val pairs = reconciliation.observeAmbiguousMatches().mapLatest { matches ->
        matches.mapNotNull { m ->
            val device = deviceDao.getById(m.match.deviceItemId) ?: return@mapNotNull null
            val cloud = cloudDao.getById(m.match.cloudItemId) ?: return@mapNotNull null
            ReviewPair(device, cloud)
        }
    }

    /** Null while loading. */
    val state: StateFlow<ReviewState?> = combine(pairs, skipped) { all, skip ->
        val open = all.filterNot { (it.cloud.id to it.device.id) in skip }
        ReviewState(current = open.firstOrNull(), remaining = open.size, skipped = all.size - open.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun decide(pair: ReviewPair, same: Boolean) {
        viewModelScope.launch { reconciliation.decide(pair.cloud.id, pair.device.id, same) }
    }

    fun skip(pair: ReviewPair) = skipped.update { it + (pair.cloud.id to pair.device.id) }

    fun showSkippedAgain() = skipped.update { emptySet() }
}

@Composable
internal fun ReviewMatchesScreen(
    onOpenDeviceItem: (Long) -> Unit,
    onOpenCloudItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewMatchesViewModel = viewModel(
        factory = appViewModelFactory { ReviewMatchesViewModel(it.reconciliation, it.database.deviceItemDao(), it.database.cloudItemDao()) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val pair = current.current
        if (pair == null) {
            MascotPanel(height = 190.dp)
            Text(stringResource(R.string.review_done), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.review_done_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (current.skipped > 0) {
                SecondaryButton(pluralStringResource(R.plurals.review_show_skipped, current.skipped, current.skipped), onClick = viewModel::showSkippedAgain)
            }
            return@Column
        }
        Text(pluralStringResource(R.plurals.review_remaining, current.remaining, current.remaining), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.review_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Side(
                title = stringResource(R.string.review_on_phone),
                source = ThumbnailSource.Local(pair.device.volume, pair.device.mediaStoreId),
                isVideo = pair.device.mime.startsWith("video/"),
                durationMs = pair.device.durationMs,
                name = pair.device.displayName,
                dateMillis = pair.device.dateTaken,
                size = Formatters.fileSize(pair.device.size),
                width = pair.device.width,
                height = pair.device.height,
                onOpen = { onOpenDeviceItem(pair.device.id) },
                modifier = Modifier.weight(1f),
            )
            Side(
                title = stringResource(R.string.review_in_google),
                source = ThumbnailSource.Remote(pair.cloud.baseUrl, pair.cloud.id, pair.cloud.sessionId),
                isVideo = pair.cloud.mime.startsWith("video/"),
                durationMs = pair.cloud.durationMs,
                name = pair.cloud.filename,
                dateMillis = pair.cloud.createTime,
                size = stringResource(R.string.review_size_unknown),
                width = pair.cloud.width,
                height = pair.cloud.height,
                onOpen = { onOpenCloudItem(pair.cloud.id) },
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(R.string.review_same), onClick = { viewModel.decide(pair, same = true) })
            SecondaryButton(stringResource(R.string.review_different), onClick = { viewModel.decide(pair, same = false) })
            TextButton(onClick = { viewModel.skip(pair) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.review_skip)) }
        }
    }
}

@Composable
private fun Side(
    title: String,
    source: ThumbnailSource,
    isVideo: Boolean,
    durationMs: Long?,
    name: String,
    dateMillis: Long,
    size: String,
    width: Int,
    height: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        MediaThumbnail(source = source, state = null, isVideo = isVideo, durationMs = durationMs, onClick = onOpen, modifier = Modifier.fillMaxWidth())
        val small = MaterialTheme.typography.bodyMedium
        Text(name, style = small)
        Text(if (dateMillis > 0) Formatters.dateTime(dateMillis) else stringResource(R.string.review_date_unknown), style = small)
        Text(size, style = small)
        Text(if (width > 0 && height > 0) "$width × $height" else stringResource(R.string.review_dimensions_unknown), style = small)
    }
}
