package com.linkedout.app.data.marks

import android.content.Context
import com.linkedout.app.core.common.writeTextAtomically
import com.linkedout.app.core.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Posts the reader liked or archived, newest mark first. */
@Serializable
data class Marks(val liked: List<Post> = emptyList(), val archived: List<Post> = emptyList()) {
    fun isLiked(id: String) = liked.any { it.id == id }
    fun isArchived(id: String) = archived.any { it.id == id }
}

/**
 * Likes and archives, kept on this phone only: nothing is sent anywhere. A
 * whole copy of each post is kept, so a liked post stays when the cache
 * drops old posts. Archived posts leave Home; both have their own view in
 * Home's title menu.
 */
class PostMarks(context: Context) {

    private val file = File(context.filesDir, "marks.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writing = Mutex()

    private val _marks = MutableStateFlow(read())
    val marks: StateFlow<Marks> = _marks.asStateFlow()

    fun toggleLike(post: Post) = change { m ->
        if (m.isLiked(post.id)) m.copy(liked = m.liked.filterNot { it.id == post.id }) else m.copy(liked = listOf(post) + m.liked)
    }

    fun toggleArchive(post: Post) = change { m ->
        if (m.isArchived(post.id)) m.copy(archived = m.archived.filterNot { it.id == post.id }) else m.copy(archived = listOf(post) + m.archived)
    }

    private fun change(transform: (Marks) -> Marks) {
        val next = transform(_marks.value)
        _marks.value = next
        scope.launch { writing.withLock { file.writeTextAtomically(json.encodeToString(Marks.serializer(), next)) } }
    }

    private fun read(): Marks =
        runCatching { json.decodeFromString(Marks.serializer(), file.readText()) }.getOrDefault(Marks())
}
