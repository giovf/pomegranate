package com.gankdat.pomegranate.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.data.model.MediaState
import com.gankdat.pomegranate.ui.components.iconRes
import com.gankdat.pomegranate.ui.components.labelRes
import com.gankdat.pomegranate.ui.format.Formatters
import com.gankdat.pomegranate.ui.theme.MediaStateColors

private sealed interface ThumbnailState {
    data object Loading : ThumbnailState

    data class Loaded(val bitmap: Bitmap) : ThumbnailState

    data object Failed : ThumbnailState
}

/** Square thumbnail with the media-state badge (top start) and a duration badge for videos (bottom end). */
@Composable
internal fun MediaThumbnail(
    source: ThumbnailSource,
    state: MediaState?,
    isVideo: Boolean,
    durationMs: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Null outside selection mode; otherwise whether this item is ticked. */
    selected: Boolean? = null,
    /** In selection mode, items that cannot be pulled are dimmed and do not react. */
    selectable: Boolean = true,
) {
    val loader = rememberThumbnailLoader()
    val thumbnail by produceState<ThumbnailState>(ThumbnailState.Loading, source) {
        value = loader.load(source)?.let { ThumbnailState.Loaded(it) } ?: ThumbnailState.Failed
    }
    val kind = if (isVideo) {
        stringResource(R.string.thumbnail_video, Formatters.duration(durationMs ?: 0))
    } else {
        stringResource(R.string.thumbnail_photo)
    }
    val base = if (state != null) "$kind, ${stringResource(state.labelRes())}" else kind
    val description = when (selected) {
        null -> base
        true -> "$base, ${stringResource(R.string.selection_selected)}"
        false -> "$base, ${stringResource(R.string.selection_not_selected)}"
    }
    val dimmed = selected != null && !selectable

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .alpha(if (dimmed) 0.35f else 1f)
            .combinedClickable(enabled = !dimmed, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description },
    ) {
        when (val current = thumbnail) {
            ThumbnailState.Loading -> Unit
            is ThumbnailState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            ThumbnailState.Failed -> Icon(
                painter = painterResource(if (source is ThumbnailSource.Remote) R.drawable.ic_state_cloud else R.drawable.ic_broken_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center),
            )
        }
        if (state != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(state.iconRes()),
                    contentDescription = null,
                    tint = MediaStateColors.of(state),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (selected == true) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        if (isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(text = Formatters.duration(durationMs ?: 0), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
