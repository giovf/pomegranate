package com.gankdat.pomegranate.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gankdat.pomegranate.PomegranateApplication
import kotlinx.coroutines.launch

/** Handles the notification's Pause action. Not exported; only this app can send it. */
internal class TransferActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAUSE) return
        val app = PomegranateApplication.from(context)
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.transferQueue.pauseAll()
                app.transferScheduler.stop()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE: String = "com.gankdat.pomegranate.transfer.PAUSE"
    }
}
