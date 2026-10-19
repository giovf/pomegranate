package com.gankdat.pomegranate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.theme.MediaStateColors

/** One-line Google connection status with picker coverage; tap to open the connection screen. */
@Composable
internal fun ConnectionStatusBar(
    isConnected: Boolean,
    cloudItemCount: Int,
    roundCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = if (isConnected) MediaStateColors.of(MediaState.BOTH) else MaterialTheme.colorScheme.outline
    val text = when {
        !isConnected -> stringResource(R.string.status_not_connected)
        roundCount == 0 -> stringResource(R.string.status_connected_no_rounds)
        else -> pluralStringResource(R.plurals.status_connected_coverage, cloudItemCount, cloudItemCount, roundCount)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
