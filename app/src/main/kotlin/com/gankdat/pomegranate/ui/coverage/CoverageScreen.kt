package com.gankdat.pomegranate.ui.coverage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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

/** Month-by-month comparison of the phone with the cloud index, to see which months still need a picker round. */
@Composable
internal fun CoverageScreen(
    modifier: Modifier = Modifier,
    viewModel: CoverageViewModel = viewModel(
        factory = appViewModelFactory { CoverageViewModel(it.database.deviceItemDao(), it.database.cloudItemDao(), it.database.matchDao(), it.reconciliation) },
    ),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val list = rows ?: return
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "intro") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.coverage_intro), style = MaterialTheme.typography.bodyMedium)
                if (list.isEmpty()) {
                    Text(stringResource(R.string.coverage_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        diagnostics?.let { d -> item(key = "diagnostics") { DiagnosticsCard(d) } }
        items(list, key = { it.month?.toString() ?: "unknown" }) { row ->
            MonthRow(row)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Why Google items were not found on the phone, and where phone-only items live. Counts only. */
@Composable
private fun DiagnosticsCard(d: MatchDiagnostics) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleSmall)
            Text(pluralStringResource(R.plurals.diag_cloud_not_on_phone, d.cloudNotOnPhone, d.cloudNotOnPhone), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            DiagLine(R.string.diag_same_name, d.sameNameDifferentDetails)
            DiagLine(R.string.diag_other_format, d.sameNameOtherFormat)
            DiagLine(R.string.diag_renamed, d.renamedCandidates)
            DiagLine(R.string.diag_none, d.noCandidate)
            DiagLine(R.string.diag_uncertain, d.uncertain)
            if (d.cloudOnlyTypes.isNotEmpty()) {
                Text(stringResource(R.string.diag_types, d.cloudOnlyTypes.joinToString { "${it.first} ${it.second}" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            if (d.phoneOnlyFolders.isNotEmpty()) {
                Text(stringResource(R.string.diag_phone_folders, d.phoneOnlyFolders.joinToString { "${it.first} ${it.second}" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun DiagLine(label: Int, count: Int) {
    Text("• " + stringResource(label, count), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun MonthRow(row: MonthCoverage) {
    val (hintText, hintColor) = when (row.hint) {
        CoverageHint.NOT_INDEXED -> stringResource(R.string.coverage_hint_not_indexed) to MediaStateColors.of(MediaState.CLOUD_ONLY)
        CoverageHint.PARTIAL -> pluralStringResource(R.plurals.coverage_hint_partial, row.deviceCount - row.deviceMatched, row.deviceCount - row.deviceMatched) to MediaStateColors.of(MediaState.CLOUD_ONLY)
        CoverageHint.COVERED -> stringResource(R.string.coverage_hint_covered) to MediaStateColors.of(MediaState.BOTH)
        CoverageHint.CLOUD_ONLY -> stringResource(R.string.coverage_hint_cloud_only) to Color.Unspecified
    }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)) {
        val title = row.month?.let { Formatters.monthYear(it.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            ?: stringResource(R.string.coverage_undated)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            pluralStringResource(R.plurals.coverage_phone_line, row.deviceCount, row.deviceCount, row.deviceMatched),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            pluralStringResource(R.plurals.coverage_cloud_line, row.cloudCount, row.cloudCount, row.cloudOnly),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(hintText, style = MaterialTheme.typography.bodySmall, color = if (hintColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else hintColor)
    }
}
