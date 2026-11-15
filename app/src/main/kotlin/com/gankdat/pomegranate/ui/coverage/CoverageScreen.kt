package com.gankdat.pomegranate.ui.coverage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.gankdat.pomegranate.ui.components.Fold
import com.gankdat.pomegranate.ui.components.SecondaryButton
import java.text.NumberFormat
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.dao.CloudItemDao
import com.gankdat.pomegranate.data.db.dao.DeviceItemDao
import com.gankdat.pomegranate.data.db.dao.MatchDao
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.CoverageCalculator
import com.gankdat.pomegranate.reconcile.CoverageHint
import com.gankdat.pomegranate.reconcile.MatchDiagnostics
import com.gankdat.pomegranate.reconcile.MonthCoverage
import com.gankdat.pomegranate.reconcile.ReconciliationRepository
import com.gankdat.pomegranate.ui.appViewModelFactory
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.theme.MediaStateColors
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class CoverageViewModel(deviceDao: DeviceItemDao, cloudDao: CloudItemDao, matchDao: MatchDao, reconciliation: ReconciliationRepository) : ViewModel() {
    /** Null while loading. */
    val rows: StateFlow<List<MonthCoverage>?> = combine(deviceDao.observeAll(), cloudDao.observeAll(), reconciliation.observeStates()) { d, c, s ->
        CoverageCalculator.byMonth(d, c, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val diagnostics: StateFlow<MatchDiagnostics?> = combine(deviceDao.observeAll(), cloudDao.observeAll(), matchDao.observeAll()) { d, c, m ->
        if (c.isEmpty()) null else MatchDiagnostics.analyze(d, c, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Month by month: how much of each month is on the phone, in Google Photos, or both, as one bar per month. */
@Composable
internal fun CoverageScreen(
    onReviewMatches: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoverageViewModel = viewModel(
        factory = appViewModelFactory { CoverageViewModel(it.database.deviceItemDao(), it.database.cloudItemDao(), it.database.matchDao(), it.reconciliation) },
    ),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val list = rows ?: return
    // Bars share one scale, so a long bar always means a busy month.
    val largest = (list.maxOfOrNull { it.deviceCount + it.cloudOnly } ?: 0).coerceAtLeast(1)
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(if (list.isEmpty()) R.string.coverage_empty else R.string.coverage_short),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (list.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MediaState.entries.forEach { place -> LegendDot(MediaStateColors.of(place), stringResource(place.legendRes())) }
                    }
                }
            }
        }
        items(list, key = { it.month?.toString() ?: "unknown" }) { row -> MonthRow(row, largest) }
        if (list.isNotEmpty()) {
            item(key = "how") {
                Fold(stringResource(R.string.coverage_how_title), modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.coverage_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        diagnostics?.let { d -> item(key = "diagnostics") { Diagnostics(d, onReviewMatches) } }
    }
}

private fun MediaState.legendRes(): Int = when (this) {
    MediaState.DEVICE_ONLY -> R.string.filter_phone
    MediaState.CLOUD_ONLY -> R.string.filter_google
    MediaState.BOTH -> R.string.filter_both
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Why Google items were not found on the phone, and where phone-only items live. Counts only, folded away. */
@Composable
private fun Diagnostics(d: MatchDiagnostics, onReviewMatches: () -> Unit) {
    Fold(stringResource(R.string.diag_title)) {
        Text(pluralStringResource(R.plurals.diag_cloud_not_on_phone, d.cloudNotOnPhone, d.cloudNotOnPhone), style = MaterialTheme.typography.bodyLarge)
        DiagLine(R.string.diag_same_name, d.sameNameDifferentDetails)
        if (d.sameNameDifferentDetails > 0) {
            Text(
                stringResource(R.string.diag_same_name_split, d.sameNameSizeDiffers, d.sameNameDateDiffers, d.sameNameBothDiffer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DiagLine(R.string.diag_other_format, d.sameNameOtherFormat)
        DiagLine(R.string.diag_renamed, d.renamedCandidates)
        DiagLine(R.string.diag_none, d.noCandidate)
        DiagLine(R.string.diag_uncertain, d.uncertain)
        if (d.uncertain > 0) {
            SecondaryButton(pluralStringResource(R.plurals.review_open, d.uncertain, d.uncertain), onClick = onReviewMatches)
        }
        if (d.cloudOnlyTypes.isNotEmpty()) {
            Text(stringResource(R.string.diag_types, d.cloudOnlyTypes.joinToString { "${it.first} ${it.second}" }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (d.phoneOnlyFolders.isNotEmpty()) {
            Text(stringResource(R.string.diag_phone_folders, d.phoneOnlyFolders.joinToString { "${it.first} ${it.second}" }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagLine(label: Int, count: Int) {
    Text(stringResource(label, count), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MonthRow(row: MonthCoverage, largest: Int) {
    val phoneOnly = (row.deviceCount - row.deviceMatched).coerceAtLeast(0)
    val (hintText, hintColor) = when (row.hint) {
        CoverageHint.NOT_INDEXED -> stringResource(R.string.coverage_hint_not_indexed) to MediaStateColors.of(MediaState.CLOUD_ONLY)
        CoverageHint.PARTIAL -> pluralStringResource(R.plurals.coverage_hint_partial, phoneOnly, phoneOnly) to MediaStateColors.of(MediaState.CLOUD_ONLY)
        CoverageHint.COVERED -> stringResource(R.string.coverage_hint_covered) to MediaStateColors.of(MediaState.BOTH)
        CoverageHint.CLOUD_ONLY -> stringResource(R.string.coverage_hint_cloud_only) to Color.Unspecified
    }
    val title = row.month?.let { Formatters.monthYear(it.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        ?: stringResource(R.string.coverage_undated)
    // The bar is the picture; the exact counts are what a screen reader says for the row.
    val spoken = listOf(
        title,
        pluralStringResource(R.plurals.coverage_phone_line, row.deviceCount, row.deviceCount, row.deviceMatched),
        pluralStringResource(R.plurals.coverage_cloud_line, row.cloudCount, row.cloudCount, row.cloudOnly),
        hintText,
    ).joinToString(". ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(NumberFormat.getIntegerInstance().format(row.deviceCount + row.cloudOnly), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val segments = listOf(
            row.deviceMatched to MediaStateColors.of(MediaState.BOTH),
            phoneOnly to MediaStateColors.of(MediaState.DEVICE_ONLY),
            row.cloudOnly to MediaStateColors.of(MediaState.CLOUD_ONLY),
        ).filter { it.first > 0 }
        val total = segments.sumOf { it.first }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            segments.forEach { (count, color) ->
                // At least a sliver, so a handful of photos in a quiet month still shows.
                Box(modifier = Modifier.weight((count.toFloat() / largest).coerceAtLeast(MIN_SEGMENT)).fillMaxHeight().background(color))
            }
            val rest = 1f - total.toFloat() / largest
            if (rest > MIN_SEGMENT) Spacer(Modifier.weight(rest))
        }
        Text(hintText, style = MaterialTheme.typography.bodyMedium, color = if (hintColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else hintColor)
    }
}

private const val MIN_SEGMENT = 0.012f
