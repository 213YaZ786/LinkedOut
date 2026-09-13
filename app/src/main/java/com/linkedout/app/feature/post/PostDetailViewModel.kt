package com.linkedout.app.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.web.ChallengeSolver
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.repository.FeedRepository
import com.linkedout.app.core.link.ShareLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ThreadState {
    data object Loading : ThreadState
    data class Ready(val conversation: Conversation) : ThreadState
    data class Failed(val error: AppError) : ThreadState
}

data class PostDetailUiState(
    val post: Post? = null,
    val lookingUp: Boolean = true,
    val thread: ThreadState = ThreadState.Loading
) {
    /** Neither the phone nor the server could produce the post. */
    val missing: Boolean get() = post == null && !lookingUp && thread !is ThreadState.Loading
}

/**
 * Shows the post at once from what the phone already has, then fetches its
 * conversation: what it answers, the author's thread, and the replies.
 * Replies are never saved, they are only worth reading fresh.
 */
class PostDetailViewModel(
    private val cache: FeedCache,
    private val repository: FeedRepository,
    private val solver: ChallengeSolver
) : ViewModel() {

    /**
     * What Share and Copy link hand out. MTGA let the reader choose which
     * server a shared link pointed at. A post has one address here, so there
     * is no choice to make and nothing to configure.
     */
    fun shareLink(post: Post): String =
        ShareLink.forPost(post.authorHandle, post.id, post.permalink)

    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null
    private var hint: String? = null

    fun load(id: String, from: String?) {
        if (loadedId == id) return
        loadedId = id
        hint = from
        viewModelScope.launch {
            // A post opened from a link is usually not on the phone. MTGA had
            // a second endpoint that returned one post cheaply and named its
            // author. LinkedIn has no equivalent, so until the post page parser
            // lands the phone's own copy is all there is.
            val known = cache.find(id, from) ?: RecentPosts.get(id)
            _state.value = PostDetailUiState(post = known, lookingUp = false, thread = ThreadState.Loading)
            fetchThread()
        }
    }

    fun retryThread() {
        if (_state.value.thread is ThreadState.Loading) return
        _state.value = _state.value.copy(thread = ThreadState.Loading)
        viewModelScope.launch { fetchThread() }
    }

    fun verify(error: AppError.ChallengeRequired) {
        viewModelScope.launch {
            val result = solver.solve(error.url, error.host, interactive = true)
            if (result is ChallengeSolver.Result.Cleared) retryThread()
        }
    }

    private suspend fun fetchThread() {
        val id = loadedId ?: return
        // No author needed. MTGA required one because a Nitter address is built
        // from the handle, and this refused to load every link that carried
        // only the number. LinkedIn's /feed/update/ form takes the id alone.
        when (val outcome = repository.loadConversation(id)) {
            is Outcome.Success -> {
                val conversation = outcome.value
                RecentPosts.remember(conversation)
                _state.value = _state.value.copy(
                    post = _state.value.post ?: conversation.main,
                    thread = ThreadState.Ready(conversation)
                )
            }
            is Outcome.Failure -> _state.value = _state.value.copy(thread = ThreadState.Failed(outcome.error))
        }
    }
}

/**
 * Posts seen in conversations during this session, so tapping a reply opens
 * it at once instead of waiting for the network. Memory only, never saved.
 */
internal object RecentPosts {
    private const val CAPACITY = 300
    private val posts = object : LinkedHashMap<String, Post>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Post>?) = size > CAPACITY
    }

    @Synchronized
    fun get(id: String): Post? = posts[id]

    @Synchronized
    fun remember(conversation: Conversation) {
        (conversation.ancestors + listOfNotNull(conversation.main) + conversation.continuation +
            conversation.replies.flatten()).forEach { posts[it.id] = it }
    }
}
