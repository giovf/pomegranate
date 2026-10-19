package com.gankdat.pomegranate.ui.manifest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gankdat.pomegranate.trust.ExportResult
import com.gankdat.pomegranate.trust.ManifestFormat
import com.gankdat.pomegranate.trust.ManifestRecord
import com.gankdat.pomegranate.trust.ManifestRepository
import com.gankdat.pomegranate.trust.ManifestSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ManifestViewModel(private val manifest: ManifestRepository) : ViewModel() {
    val summary: StateFlow<ManifestSummary> = manifest.observeSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManifestSummary())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val filteredRecords: StateFlow<List<ManifestRecord>> = combine(manifest.observeRecords(), _query) { records, q ->
        val needle = q.trim()
        if (needle.isEmpty()) records.asReversed() else records.asReversed().filter { matches(it, needle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val results = Channel<ExportResult>(Channel.BUFFERED)
    val exportResults: Flow<ExportResult> = results.receiveAsFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun export(format: ManifestFormat) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                results.send(manifest.export(format))
            } finally {
                _exporting.value = false
            }
        }
    }

    companion object {
        fun matches(record: ManifestRecord, needle: String): Boolean =
            record.originalFilename.contains(needle, ignoreCase = true) ||
                record.destinationPath.contains(needle, ignoreCase = true) ||
                record.sha256.startsWith(needle, ignoreCase = true) ||
                record.sourceId == needle
    }
}
