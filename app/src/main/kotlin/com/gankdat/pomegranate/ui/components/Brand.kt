package com.gankdat.pomegranate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.gankdat.pomegranate.R

/**
 * The Pomegranate character on its peach ground with a few seeds around it:
 * the app's one illustration, used where a screen greets or celebrates.
 * Purely decorative, so it is hidden from screen readers.
 */
@Composable
internal fun MascotPanel(modifier: Modifier = Modifier, height: Dp = 220.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val unit = height / 220
        Image(painter = painterResource(R.drawable.ic_mascot), contentDescription = null, modifier = Modifier.size(width = unit * 118, height = unit * 150))
        Seed(x = unit * 84, y = -unit * 58, degrees = 32f, size = unit * 26)
        Seed(x = -unit * 88, y = -unit * 40, degrees = -38f, size = unit * 22)
        Seed(x = unit * 96, y = unit * 14, degrees = 72f, size = unit * 18)
    }
}

@Composable
private fun Seed(x: Dp, y: Dp, degrees: Float, size: Dp) {
    Image(
        painter = painterResource(R.drawable.ic_seed),
        contentDescription = null,
        modifier = Modifier.offset(x = x, y = y).size(width = size * 22 / 31, height = size).rotate(degrees),
    )
}

/** A short reassurance with a tick in front of it. */
@Composable
internal fun PromiseRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The one main action of a screen: full width and tall enough to hit easily. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A second, quieter action next to a [PrimaryButton]. */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        border = BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A heading row that opens and closes what is under it; closed to begin with, so detail stays out of the way until wanted. */
@Composable
internal fun Fold(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier = modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.group_collapse else R.string.group_expand)) { open = !open }
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) Column(modifier = Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
