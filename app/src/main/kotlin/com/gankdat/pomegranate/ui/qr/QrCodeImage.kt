package com.gankdat.pomegranate.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val QUIET_ZONE = 4

/** Renders [text] as a QR code. Always black on white with the standard quiet zone, whatever the theme, so phone cameras can read it. */
@Composable
internal fun QrCodeImage(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val qr = remember(text) { QrCode.encode(text) }
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val cells = qr.size + QUIET_ZONE * 2
        val cell = size.minDimension / cells
        drawRect(Color.White)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.isDark(x, y)) {
                    // Slight overlap avoids hairline gaps between modules from anti-aliasing.
                    drawRect(Color.Black, topLeft = Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), size = Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
