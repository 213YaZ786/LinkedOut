package com.linkedout.app.core.media

import android.content.Context
import com.linkedout.app.core.common.writeTextAtomically
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The files automatic saving has asked for, so a later pass does not ask again.
 *
 * Ported from MTGA, https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/SaveAttempts.kt#SaveAttempts
 *
 * Without it a pass over the stored posts would queue a file still downloading,
 * and a file that always fails (a video whose signed address ran out) would sit
 * at the head of the newest first list at every opening and starve everything
 * older. A name asked for stays out of the passes for [BACKOFF_MILLIS]: long
 * enough for the system's downloader to finish or give up, short enough that a
 * file lost to a bad network gets another go.
 *
 * One small JSON file in the app's no backup folder, written in one step
 * (writeTextAtomically).
 */
class SaveAttempts(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis
) {

    @Serializable
    private data class Doc(val asked: Map<String, Long> = emptyMap())

    private val lock = Any()
    private var asked: Map<String, Long>? = null

    /** True when [name] was asked for less than [BACKOFF_MILLIS] ago. */
    fun recently(name: String): Boolean = synchronized(lock) {
        val at = read()[name] ?: return false
        clock() - at in 0 until BACKOFF_MILLIS
    }

    /** Notes that [names] were asked for now. The oldest notes go past [MAX_ENTRIES]. */
    fun add(names: Collection<String>) {
        if (names.isEmpty()) return
        synchronized(lock) {
            val now = clock()
            val live = read().filterValues { now - it in 0 until BACKOFF_MILLIS }
            val next = (live + names.associateWith { now }).entries
                .sortedBy { it.value }
                .takeLast(MAX_ENTRIES)
                .associate { it.key to it.value }
            asked = next
            runCatching { file.writeTextAtomically(JSON.encodeToString(Doc(next))) }
        }
    }

    private fun read(): Map<String, Long> =
        asked ?: (runCatching { JSON.decodeFromString<Doc>(file.readText()).asked }.getOrNull() ?: emptyMap())
            .also { asked = it }

    companion object {
        const val BACKOFF_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_ENTRIES = 20_000

        private val JSON = Json { ignoreUnknownKeys = true }

        fun of(context: Context): SaveAttempts =
            SaveAttempts(File(context.noBackupFilesDir, "save-attempts.json"))
    }
}
