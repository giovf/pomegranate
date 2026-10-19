package com.gankdat.pomegranate.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.theme.MediaStateColors

/** Icon and label pairing for a [MediaState]; the single source of the state's visual identity. */
@DrawableRes
internal fun MediaState.iconRes(): Int = when (this) {
    MediaState.DEVICE_ONLY -> R.drawable.ic_state_device
    MediaState.CLOUD_ONLY -> R.drawable.ic_state_cloud
    MediaState.BOTH -> R.drawable.ic_state_both
}

@StringRes
internal fun MediaState.labelRes(): Int = when (this) {
    MediaState.DEVICE_ONLY -> R.string.media_state_device_only
    MediaState.CLOUD_ONLY -> R.string.media_state_cloud_only
    MediaState.BOTH -> R.string.media_state_both
}

/** A tinted icon for [state]; the label is always in the content description and optionally shown. */
@Composable
internal fun MediaStateIndicator(
    state: MediaState,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val label = stringResource(state.labelRes())
    val tint = MediaStateColors.of(state)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(state.iconRes()),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (showLabel) {
            Text(text = label, color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}
