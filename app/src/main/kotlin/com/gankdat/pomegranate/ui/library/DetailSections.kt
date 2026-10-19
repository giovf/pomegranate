package com.gankdat.pomegranate.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.db.entity.MatchConfidence
import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.reconcile.MatchMethod
import com.gankdat.pomegranate.reconcile.TimestampAgreement
import com.gankdat.pomegranate.ui.components.MediaStateIndicator

/** Status header shared by both detail screens. */
@Composable
internal fun StatusSection(state: MediaState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            MediaStateIndicator(state = state, showLabel = true)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (state) {
                        MediaState.BOTH -> R.string.status_explain_both
                        MediaState.CLOUD_ONLY -> R.string.status_explain_cloud_only
                        MediaState.DEVICE_ONLY -> R.string.status_explain_device_only
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** What matched and how confidently, with a link to the other half of the pair. */
@Composable
internal fun MatchSection(match: MatchInfo, onOpenCounterpart: (() -> Unit)?) {
    Text(stringResource(R.string.section_match), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(4.dp))
    val confidence = stringResource(
        when (match.confidence) {
            MatchConfidence.EXACT -> R.string.confidence_exact
            MatchConfidence.LIKELY -> R.string.confidence_likely
            MatchConfidence.AMBIGUOUS -> R.string.confidence_ambiguous
        },
    )
    Text(confidence, style = MaterialTheme.typography.bodyMedium)
    match.evidence?.let { e ->
        val reasons = buildList {
            if (e.filenameMatched) add(stringResource(R.string.evidence_filename))
            when (e.timestamp) {
                TimestampAgreement.EXACT -> add(stringResource(R.string.evidence_time_exact))
                TimestampAgreement.ZONE_SHIFTED -> add(stringResource(R.string.evidence_time_zone))
                TimestampAgreement.DIFFERENT -> add(stringResource(R.string.evidence_time_different))
                TimestampAgreement.UNKNOWN -> add(stringResource(R.string.evidence_time_unknown))
            }
            if (e.dimensionsMatched) add(stringResource(R.string.evidence_dimensions))
            if (e.hashMatched == true || e.method == MatchMethod.HASH_CONFIRMED) add(stringResource(R.string.evidence_hash))
            if (e.candidateCount > 1) add(pluralStringResource(R.plurals.evidence_candidates, e.candidateCount, e.candidateCount))
        }
        Text(reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val counterpart = match.deviceItem?.displayName ?: match.cloudItem?.filename
    if (counterpart != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(counterpart, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            if (onOpenCounterpart != null) {
                TextButton(onClick = onOpenCounterpart) {
                    Text(stringResource(if (match.deviceItem != null) R.string.open_device_file else R.string.open_cloud_item))
                }
            }
        }
    }
}

@Composable
internal fun SiblingsSection(siblings: List<SiblingInfo>) {
    Text(stringResource(R.string.section_siblings), style = MaterialTheme.typography.labelLarge)
    if (siblings.isEmpty()) {
        Text(stringResource(R.string.siblings_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    siblings.forEach { group ->
        val kind = stringResource(
            when (group.kind) {
                MediaGroupKind.SINGLE -> R.string.group_kind_single
                MediaGroupKind.LIVE_PHOTO -> R.string.group_kind_live
                MediaGroupKind.MOTION_PHOTO -> R.string.group_kind_motion
                MediaGroupKind.RAW_PAIR -> R.string.group_kind_raw
                MediaGroupKind.EDIT_PAIR -> R.string.group_kind_edit
            },
        )
        Text("$kind: ${group.filenames.joinToString()}", style = MaterialTheme.typography.bodyMedium)
    }
}
