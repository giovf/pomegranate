package com.gankdat.pomegranate.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.gankdat.pomegranate.MainActivity
import com.gankdat.pomegranate.R

/** The persistent "pulling media" notification that keeps the transfer worker in the foreground. */
internal object TransferNotifications {
    const val CHANNEL_ID: String = "transfer_progress"
    const val NOTIFICATION_ID: Int = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_transfers), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_transfers_description)
                setShowBadge(false)
            },
        )
    }

    fun foregroundInfo(context: Context, summary: TransferSummary): ForegroundInfo =
        ForegroundInfo(NOTIFICATION_ID, build(context, summary), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    fun build(context: Context, summary: TransferSummary): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PULL, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TransferActionReceiver::class.java).setAction(TransferActionReceiver.ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.notification_pulling_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notification_pulling_text, summary.total, summary.done, summary.total))
            .setProgress(summary.total.coerceAtLeast(1), summary.done, summary.total == 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.pull_pause), pause)
            .build()
    }
}
