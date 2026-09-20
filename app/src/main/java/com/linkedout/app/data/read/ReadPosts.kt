package com.linkedout.app.data.read

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * What the reader has already gone past on Home.
 *
 * A post keeps a thick outline until its top leaves the screen by the top,
 * which is to say until the reader has scrolled below it. There is no timer
 * and no tap to dismiss, and a refresh cannot wipe three outlines at once
 * since those posts are still on screen.
 *
 * Two rules keep this from lying to the reader.
 *
 * Until the file has been read back, everything counts as read. Without that,
 * Home would paint one whole frame with every card outlined before the disk
 * answers, a blue flash at each launch.
 *
 * At launch, everything the cache already held is marked read by
 * [com.linkedout.app.feature.timeline.TimelineViewModel]. You never come back
 * to a wall of outlines from the day before, only to what arrived since.
 */
class ReadPosts(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "read-posts.txt")

    private val _state = MutableStateFlow(ReadState())
    val state: StateFlow<ReadState> = _state.asStateFlow()

    /** One writer at a time. Every pass of the list can ask to record ids. */
    private val work = Mutex()

    /** Completed once the file has been read, so a mark can wait for it. */
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val stored = runCatching {
                if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
            }.getOrDefault(emptyList())
            work.withLock {
                _state.value = ReadState(ready = true, ids = cap(LinkedHashSet(stored)))
            }
            loaded.complete(Unit)
        }
    }

    /**
     * Records ids as read. Cheap to call on every pass of the list: ids
     * already known cost nothing, and new ones are appended to the file
     * rather than rewriting all of it.
     */
    fun mark(ids: Collection<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            loaded.await()
            work.withLock {
                val current = _state.value
                val fresh = ids.filterNot { it in current.ids }
                if (fresh.isEmpty()) return@withLock

                val merged = LinkedHashSet(current.ids)
                merged.addAll(fresh)
                val capped = cap(merged)
                _state.value = current.copy(ids = capped)

                runCatching {
                    // The cap is the only case that has to rewrite, because
                    // the head of the file is what just went away.
                    if (capped.size < merged.size) {
                        file.writeText(capped.joinToString("\n"))
                    } else {
                        file.appendText(fresh.joinToString("\n", postfix = "\n"))
                    }
                }
            }
        }
    }

    /**
     * Oldest first out. Insertion order, not the numeric order of the ids:
     * a LinkedIn activity id is a number but nothing here has confirmed that
     * it always grows with time, and reading order is a fact this class owns.
     */
    private fun cap(ids: LinkedHashSet<String>): LinkedHashSet<String> =
        if (ids.size <= LIMIT) ids else LinkedHashSet(ids.drop(ids.size - LIMIT))

    companion object {
        /**
         * Far above what the cache can hold, so a post cannot be forgotten
         * here and then shown as new again.
         */
        const val LIMIT = 20_000
    }
}

/** [ready] is false until the file has been read. Everything is read until then. */
data class ReadState(val ready: Boolean = false, val ids: Set<String> = emptySet()) {
    fun isUnread(id: String): Boolean = ready && id !in ids
}
