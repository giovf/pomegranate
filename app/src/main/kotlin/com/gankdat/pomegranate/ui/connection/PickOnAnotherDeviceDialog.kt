package com.gankdat.pomegranate.ui.connection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.qr.QrCodeImage

/**
 * Shows the Picker session link as a QR code, with copy and share, so the
 * selection can be made on a computer (where shift-click range selection is
 * fast). The phone keeps polling the session and indexes it when Done is
 * pressed on the other device. The link only works for the signed-in owner
 * of the Google account.
 */
@Composable
internal fun PickOnAnotherDeviceDialog(pickerUri: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_other_device_title)) },
        text = {
            Column {
                Text(stringResource(R.string.picker_other_device_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                QrCodeImage(text = pickerUri, contentDescription = stringResource(R.string.picker_qr_description), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(pickerUri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { context.copyLink(pickerUri) }) { Text(stringResource(R.string.picker_copy_link)) }
                    OutlinedButton(onClick = { context.shareLink(pickerUri) }) { Text(stringResource(R.string.picker_share_link)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.picker_other_device_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_other_device_done)) } },
    )
}

private fun Context.copyLink(url: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.picker_link_label), url))
}

private fun Context.shareLink(url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
