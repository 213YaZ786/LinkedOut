package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * Settles the saves of [SaveOrigin] when a download finishes.
 *
 * DownloadManager sends ACTION_DOWNLOAD_COMPLETE to the app that asked for
 * the download (https://developer.android.com/reference/android/app/DownloadManager#ACTION_DOWNLOAD_COMPLETE),
 * so the app is woken only by its own downloads. The work is a few queries
 * and one update, done on a thread of its own while goAsync keeps the
 * receiver alive
 * (https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
 *
 * Declared in the manifest as exported, which a system broadcast needs, and
 * protected by android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS, which only
 * the system's download provider holds: no other app can reach it.
 */
class SaveOriginReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!accepts(intent.action, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))) return
        val pending = goAsync()
        val app = context.applicationContext
        thread(name = "save-origin") {
            try {
                // The media scanner may be a moment behind the broadcast.
                SaveOrigin.settle(app, retries = 2)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        /** Only a finished download with an id is worth a look, whatever else arrives. */
        fun accepts(action: String?, downloadId: Long): Boolean =
            action == DownloadManager.ACTION_DOWNLOAD_COMPLETE && downloadId >= 0
    }
}
