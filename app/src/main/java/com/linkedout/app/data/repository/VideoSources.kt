package com.linkedout.app.data.repository

import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Finds the address of a video that a profile page named but did not carry.
 *
 * A profile document renders a cover picture and a play badge for each video
 * and no source anywhere, while the post's own page serves a VideoObject with
 * its contentUrl. So the address exists, one page further.
 *
 * Asked for on the first tap and not before. Reading a profile with eight
 * videos would otherwise mean eight extra requests the moment it opens, against
 * a host that answers 999 when it has had enough, for films nobody may watch.
 *
 * Answers are kept for the life of the process. Opening the same video twice,
 * or scrolling away and back, costs one request in total. The lock makes two
 * taps in a row wait on one request rather than sending two.
 */
class VideoSources(private val feeds: FeedRepository) {

    private val known = mutableMapOf<String, String>()
    private val lock = Mutex()

    /** The playable address for [postId], or null when the page had none. */
    suspend fun sourceFor(postId: String): String? {
        known[postId]?.let { return it }
        return lock.withLock {
            known[postId] ?: fetch(postId)?.also { known[postId] = it }
        }
    }

    private suspend fun fetch(postId: String): String? =
        when (val outcome = feeds.loadConversation(postId)) {
            is Outcome.Failure -> null
            is Outcome.Success -> outcome.value.main
                ?.media
                ?.firstOrNull { it.type == MediaType.VIDEO }
                ?.downloadUrl
                ?.takeIf { it.isNotBlank() }
        }
}
