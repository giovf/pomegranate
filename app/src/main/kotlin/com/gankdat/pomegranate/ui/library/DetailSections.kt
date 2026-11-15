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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.theme.MediaStateColors
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = MediaStateColors.of(state).copy(alpha = 0.14f)) {
            MediaStateIndicator(state = state, showLabel = true, modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp))
        }
        Text(
            text = stringResource(
                when (state) {
                    MediaState.BOTH -> R.string.status_explain_both
                    MediaState.CLOUD_ONLY -> R.string.status_explain_cloud_only
                    MediaState.DEVICE_ONLY -> R.string.status_explain_device_only
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** When it was taken, large, with the file name under it: what a person looks for first. */
@Composable
internal fun DetailTitle(takenAtMillis: Long, name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Formatters.dateTime(takenAtMillis), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Everything technical about an item, closed until asked for. */
@Composable
internal fun DetailsFold(content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.group_collapse else R.string.group_expand)) { open = !open }
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.details_more), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) Column(modifier = Modifier.padding(bottom = 16.dp)) { content() }
    }
}

/** What matched and how confidently, with a link to the other half of the pair. */
@Composable
internal fun MatchSection(match: MatchInfo, onOpenCounterpart: (() -> Unit)?) {
    Text(stringResource(R.string.section_match), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
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
            if (e.method == MatchMethod.PULLED) add(stringResource(R.string.evidence_pulled))
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
    Text(stringResource(R.string.section_siblings), style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
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
