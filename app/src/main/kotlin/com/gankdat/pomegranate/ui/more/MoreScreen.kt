package com.gankdat.pomegranate.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R

/**
 * Everything that is not part of the main move: the proof, the Google
 * connection, the detailed views for people who want them, and settings.
 */
@Composable
internal fun MoreScreen(
    onOpenProof: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenCleanupGuide: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Group(stringResource(R.string.more_copies)) {
            MoreRow(stringResource(R.string.more_proof), onOpenProof, divider = false)
        }
        Group(stringResource(R.string.connection_title)) {
            MoreRow(stringResource(R.string.more_connection), onOpenConnection)
            MoreRow(stringResource(R.string.title_cleanup_guidance), onOpenCleanupGuide, divider = false)
        }
        Group(stringResource(R.string.more_closer)) {
            MoreRow(stringResource(R.string.more_months), onOpenCoverage)
            MoreRow(stringResource(R.string.more_lookalikes), onOpenReview, divider = false)
        }
        Group(title = null) {
            MoreRow(stringResource(R.string.title_settings), onOpenSettings, divider = false)
        }
    }
}

@Composable
private fun Group(title: String?, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column { rows() }
        }
    }
}

@Composable
private fun MoreRow(title: String, onClick: () -> Unit, divider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
}
