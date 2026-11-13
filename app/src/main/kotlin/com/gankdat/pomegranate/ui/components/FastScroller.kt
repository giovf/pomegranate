package com.gankdat.pomegranate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Pure position maths for [FastScroller]. */
internal object FastScroll {
    /** How far down the list is, 0..1, from the first visible item. */
    fun fraction(firstVisible: Int, visibleCount: Int, total: Int): Float {
        val last = total - visibleCount
        return if (last <= 0) 0f else (firstVisible.toFloat() / last).coerceIn(0f, 1f)
    }

    /** The item to jump to for a thumb position of [fraction] (0..1). */
    fun index(fraction: Float, total: Int): Int =
        if (total <= 0) 0 else (fraction.coerceIn(0f, 1f) * (total - 1)).roundToInt()
}

/**
 * A handle on the right edge that follows the list and can be dragged to jump
 * through it. While dragging, a bubble shows [labelFor] the first visible
 * item (the date or folder), so the user can see where they are.
 * Place it in a Box over the list, aligned to the end.
 */
@Composable
internal fun FastScroller(listState: LazyListState, labelFor: (Int) -> String?, modifier: Modifier = Modifier) {
    val scrollable by remember(listState) { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    if (!scrollable) return

    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    val thumbHeightPx = with(LocalDensity.current) { THUMB_HEIGHT.toPx() }
    val fraction by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            FastScroll.fraction(listState.firstVisibleItemIndex, info.visibleItemsInfo.size, info.totalItemsCount)
        }
    }
    val range = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
    val restingOffset by rememberUpdatedState(fraction * range)
    val offset = dragOffset ?: restingOffset
    val currentLabelFor by rememberUpdatedState(labelFor)
    // Derived so the bubble recomposes when the label changes, not on every scrolled row.
    val firstVisibleLabel by remember(listState) { derivedStateOf { currentLabelFor(listState.firstVisibleItemIndex) } }
    val label = if (dragOffset != null) firstVisibleLabel else null
    val description = stringResource(R.string.fast_scroll_handle)

    Box(modifier = modifier.fillMaxHeight().onSizeChanged { trackHeight = it.height }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, offset.roundToInt()) },
        ) {
            if (label != null) {
                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            // 48dp-wide touch target around a narrower visible handle.
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .size(width = 48.dp, height = THUMB_HEIGHT)
                    .semantics { contentDescription = description }
                    .pointerInput(range) {
                        detectVerticalDragGestures(
                            onDragStart = { dragOffset = restingOffset },
                            onDragEnd = { dragOffset = null },
                            onDragCancel = { dragOffset = null },
                        ) { change, dragAmount ->
                            change.consume()
                            val next = ((dragOffset ?: restingOffset) + dragAmount).coerceIn(0f, range)
                            dragOffset = next
                            val target = FastScroll.index(if (range > 0f) next / range else 0f, listState.layoutInfo.totalItemsCount)
                            scope.launch { listState.scrollToItem(target) }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = THUMB_HEIGHT)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .background(if (dragOffset != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
            }
        }
    }
}

private val THUMB_HEIGHT = 52.dp
