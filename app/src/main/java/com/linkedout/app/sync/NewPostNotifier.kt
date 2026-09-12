package com.linkedout.app.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.linkedout.app.MainActivity
import com.linkedout.app.R
import com.linkedout.app.core.model.Post

/**
 * One notification per new post, bundled under a single LinkedOut summary.
 *
 * Before 2.4.3 each check replaced the previous notification, so posts not
 * yet read were lost when the next check found something. Now posts pile up
 * in the shade until read or swiped away, the way people expect. Android
 * shows them as one expandable group, a tap on a post opens that post in
 * LinkedOut through the same x.com link path as a shared link, and a tap on the
 * summary opens Home.
 *
 * A check makes one sound at most, from the summary. At most [MAX_ACTIVE]
 * posts stay in the shade, the oldest go first, well under Android's limit
 * per app. Nothing leaves the device: the text is what the check saved.
 */
class NewPostNotifier(private val context: Context) {

    /** False when Android would drop the notification anyway. */
    fun canNotify(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun show(posts: List<Post>) {
        if (posts.isEmpty() || !canNotify()) return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)

        // Read before posting: Android registers new notifications a moment
        // later, so the shade is combined with this check by hand.
        val earlier = inShade()

        // Newest first. A check that found a flood posts its newest few and
        // leaves the rest to Home, rather than filling the shade.
        val shown = posts.sortedByDescending { it.publishedAtMillis }.take(MAX_PER_CHECK)
        shown.forEach { post ->
            runCatching { manager.notify(post.id, POST_ID, postNotification(post)) }
        }

        val shownIds = shown.map { it.id }.toSet()
        val all = (shown.map { Entry(it.id, it.publishedAtMillis, it.authorHandle, textOf(it)) } +
            earlier.filterNot { it.tag in shownIds })
            .sortedByDescending { it.whenMillis }

        // Keep the shade to MAX_ACTIVE posts, the oldest go first.
        all.drop(MAX_ACTIVE).forEach { runCatching { manager.cancel(it.tag, POST_ID) } }
        val kept = all.take(MAX_ACTIVE)

        runCatching { manager.notify(SUMMARY_ID, summary(kept, extra = posts.size - shown.size)) }
    }

    /** One post notification, as the summary needs it. */
    private data class Entry(val tag: String, val whenMillis: Long, val handle: String, val text: String)

    private fun postNotification(post: Post) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(post.publishedAtMillis)
            .setShowWhen(true)
            .setContentTitle("${post.authorName}  @${post.authorHandle}")
            .setContentText(textOf(post))
            .setStyle(NotificationCompat.BigTextStyle().bigText(textOf(post)))
            .setContentIntent(openPost(post))
            .setGroup(GROUP)
            // Only the summary sounds, once per check, not once per post.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .build()

    /**
     * Covers every post in the shade, including unread ones from earlier
     * checks. [extra] counts posts of this check left to Home.
     */
    private fun summary(entries: List<Entry>, extra: Int): Notification {
        val handles = entries.map { "@${it.handle}" }.distinct()
        val from = when {
            handles.isEmpty() -> "From the accounts you follow"
            handles.size == 1 -> "From ${handles[0]}"
            handles.size == 2 -> "From ${handles[0]} and ${handles[1]}"
            else -> "From ${handles[0]}, ${handles[1]} and ${handles.size - 2} more"
        }
        val total = entries.size + extra
        val inbox = NotificationCompat.InboxStyle()
        entries.take(INBOX_LINES).forEach { entry ->
            val line = entry.text.lineSequence().firstOrNull { it.isNotBlank() } ?: "Posted media"
            inbox.addLine("@${entry.handle}  $line")
        }
        if (total > INBOX_LINES) inbox.setSummaryText("${total - INBOX_LINES} more in Home")

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_linkedout)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentTitle(if (total == 1) "1 new post" else "$total new posts")
            .setContentText(from)
            .setStyle(inbox)
            .setNumber(total)
            .setContentIntent(openHome())
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .build()
    }

    /** LinkedOut's post notifications currently in the shade. */
    private fun inShade(): List<Entry> {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return emptyList()
        val active: List<StatusBarNotification> =
            runCatching { manager.activeNotifications.toList() }.getOrDefault(emptyList())
        return active.filter { it.id == POST_ID && it.tag != null }.map { sbn ->
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
            Entry(
                tag = sbn.tag,
                whenMillis = sbn.notification.`when`,
                handle = title.substringAfterLast("@", "").trim(),
                text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
            )
        }
    }

    private fun textOf(post: Post): String =
        post.text.ifBlank { if (post.media.isNotEmpty()) "Posted media" else "New post" }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New posts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Posts found by background checks from the accounts you follow"
            }
        )
    }

    /** An x.com link, handled by LinkRouter like any shared link, so it opens the post here. */
    private fun openPost(post: Post): PendingIntent {
        val url = "https://x.com/${post.authorHandle}/status/${post.id}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_POST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openHome(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, REQUEST_HOME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "new-posts"
        private const val GROUP = "com.linkedout.app.NEW_POSTS"

        /** The summary. Also the id the single notification used before 2.4.3, so an old one is replaced. */
        private const val SUMMARY_ID = 1

        /** Every post shares this id, told apart by its tag, the post id. */
        private const val POST_ID = 2
        private const val REQUEST_POST = 1
        private const val REQUEST_HOME = 2
        private const val INBOX_LINES = 5
        private const val MAX_PER_CHECK = 8
        private const val MAX_ACTIVE = 24
    }
}
