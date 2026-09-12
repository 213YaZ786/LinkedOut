package com.linkedout.app.core.link

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a tapped link goes.
 *
 * Two callers, one rule. The activity hands over intents from other apps, and
 * the composition hands over every link inside a post. Anything this app can
 * show opens here, anything else goes to the browser.
 *
 * A share from the LinkedIn app arrives as ACTION_SEND with the URL buried in
 * a sentence rather than as a bare intent data URI, which is why the text is
 * scanned rather than parsed whole.
 */
class LinkRouter(private val context: Context) {

    private val _pending = MutableStateFlow<LinkedInLink?>(null)

    /** The link waiting to be shown, or null. Cleared by [consume]. */
    val pending: StateFlow<LinkedInLink?> = _pending.asStateFlow()

    /** Null when this is not a LinkedIn address the app can show. */
    fun parse(uri: String): LinkedInLink? = LinkedInLink.parse(uri)

    /**
     * Takes the link out of [intent] if there is one. True when it was taken,
     * which tells the activity not to fall back to the browser.
     */
    fun offer(intent: Intent?): Boolean {
        val link = intent?.let(::linkIn) ?: return false
        _pending.value = link
        return true
    }

    fun consume() {
        _pending.value = null
    }

    private fun linkIn(intent: Intent): LinkedInLink? {
        val direct = intent.dataString?.let(::parse)
        if (direct != null) return direct
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return parse(shared) ?: LinkedInLink.firstUrlIn(shared)?.let(::parse)
    }

    companion object {

        /**
         * Hands a URL to whatever else is installed. Used for a LinkedIn page
         * this app has no screen for, a job or a group, and for every link a
         * post points at.
         *
         * Wrapped because a phone with no browser at all throws, and losing a
         * tap is better than losing the app.
         */
        fun openOutside(context: Context, url: String) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
