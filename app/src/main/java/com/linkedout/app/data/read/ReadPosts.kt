package com.linkedout.app.data.read

import com.linkedout.app.core.common.writeTextAtomically
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * At launch, what the cache held and was published before the previous visit
 * is marked read by [markReadBeforeLastVisit] (LinkedOutApplication). You never
 * come back to a wall of outlines from the day before, only to what arrived
 * since. Posts that arrive later but were published before that visit (an older
 * page, a profile read again) are marked by [markReadIfOld].
 *
 * Ported from MTGA's ReadMarks, which keeps the same rule:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/read/ReadMarks.kt
 * not ported: load (the read of the file is this class's init, which also
 * keeps the lines in order), markRead (this class's [mark]), trim and MAX_IDS
 * (this class's cap and LIMIT, which drop the oldest read rather than the
 * lowest id, since a LinkedIn id is not known to grow with time).
 */
class ReadPosts(filesDir: File, private val scope: CoroutineScope) {

    constructor(context: Context, scope: CoroutineScope) : this(context.filesDir, scope)

    private val file = File(filesDir, "read-posts.txt")

    private val visitFile = File(filesDir, "last-visit.txt")

    /**
     * When the reader last opened the app before this launch. Until the
     * launch has read it, every post counts as old, as in MTGA.
     */
    @Volatile
    private var previousVisit: Long = Long.MAX_VALUE

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
        scope.launch { add(ids) }
    }

    /**
     * Posts added after launch, an older page or a profile read again: what
     * was published before the previous visit is not new, it only filled a gap.
     *
     * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/read/ReadMarks.kt#markReadIfOld
     */
    suspend fun markReadIfOld(posts: List<Pair<String, Long>>) =
        add(posts.filter { it.second <= previousVisit }.map { it.first }.toSet())

    /**
     * Run at launch over the posts the cache holds: what was published
     * before the previous visit is not new, whoever stored it. Posts the
     * background checks saved since, while the app was closed, stay new
     * until scrolled past. The very first launch has no previous visit, and
     * nothing is new then.
     *
     * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/read/ReadMarks.kt#markReadBeforeLastVisit
     */
    suspend fun markReadBeforeLastVisit(posts: Map<String, Long>) {
        val previous = withContext(Dispatchers.IO) { runCatching { visitFile.readText().trim().toLong() }.getOrNull() }
        previousVisit = previous ?: System.currentTimeMillis()
        add(posts.filterValues { previous == null || it <= previous }.keys)
        withContext(Dispatchers.IO) { runCatching { visitFile.writeTextAtomically(System.currentTimeMillis().toString()) } }
    }

    private suspend fun add(ids: Collection<String>) {
        if (ids.isEmpty()) return
        loaded.await()
        work.withLock {
            val current = _state.value
            val fresh = ids.filterNot { it in current.ids }
            if (fresh.isEmpty()) return@withLock

            val merged = LinkedHashSet(current.ids)
            merged.addAll(fresh)
            val capped = cap(merged)
            _state.value = current.copy(ids = capped)

            withContext(Dispatchers.IO) {
                runCatching {
                    // The cap is the only case that has to rewrite, because
                    // the head of the file is what just went away.
                    if (capped.size < merged.size) {
                        file.writeTextAtomically(capped.joinToString("\n"))
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
