package com.gankdat.pomegranate.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.format.Formatters

private sealed interface ThumbnailState {
    data object Loading : ThumbnailState

    data class Loaded(val bitmap: Bitmap) : ThumbnailState

    data object Failed : ThumbnailState
}

/** Square thumbnail with a duration badge for videos. */
@Composable
internal fun MediaThumbnail(
    contentUri: Uri,
    isVideo: Boolean,
    durationMs: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loader = rememberThumbnailLoader()
    val state by produceState<ThumbnailState>(ThumbnailState.Loading, contentUri) {
        value = loader.load(contentUri)?.let { ThumbnailState.Loaded(it) } ?: ThumbnailState.Failed
    }
    val description = if (isVideo) {
        stringResource(R.string.thumbnail_video, Formatters.duration(durationMs ?: 0))
    } else {
        stringResource(R.string.thumbnail_photo)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        when (val current = state) {
            ThumbnailState.Loading -> Unit
            is ThumbnailState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            ThumbnailState.Failed -> Icon(
                painter = painterResource(R.drawable.ic_broken_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center),
            )
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
