package com.linkedout.app.core.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.linkedout.app.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * A silent notification while a long load runs in the background, so the
 * reader sees something is happening: "Looking up older posts" while the
 * archives answer, for instance. Several loads at once share it; it goes
 * when the last one ends. Nothing when notifications are not allowed.
 */
class LoadingNotice(private val context: Context) {

    private val running = AtomicInteger(0)

    suspend fun <T> during(text: String, work: suspend () -> T): T {
        if (running.getAndIncrement() == 0) show(text)
        try {
            return work()
        } finally {
            if (running.decrementAndGet() == 0) hide()
        }
    }

    private fun show(text: String) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Loading", NotificationManager.IMPORTANCE_LOW))
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setContentTitle("LinkedOut")
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { notifications.notify(ID, note) }
    }

    private fun hide() {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private companion object {
        const val CHANNEL = "loading"
        const val ID = 7301
    }
}
