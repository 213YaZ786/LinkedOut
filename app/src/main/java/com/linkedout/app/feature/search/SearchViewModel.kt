package com.linkedout.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.cache.FeedCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loaded: Boolean = false,
    val savedCount: Int = 0,
    val results: List<Post> = emptyList()
)

/**
 * Searches the posts saved on this phone. Works offline and sends nothing
 * anywhere. Every word typed must appear, in any order, in the text, the
 * author's name or handle, or the quoted post.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(private val cache: FeedCache) : ViewModel() {

    private val query = MutableStateFlow("")
    private val posts = MutableStateFlow<List<Post>?>(null)

    val state: StateFlow<SearchUiState> = combine(query.debounce(150), posts) { q, all ->
        val words = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        SearchUiState(
            query = q,
            loaded = all != null,
            savedCount = all?.size ?: 0,
            results = if (all == null || words.isEmpty()) emptyList() else all.filter { it.matches(words) }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Kept outside [state] so the field never lags behind typing. */
    val typed: StateFlow<String> = query

    init {
        viewModelScope.launch { posts.value = cache.allPosts() }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    private fun Post.matches(words: List<String>): Boolean {
        val haystack = buildString {
            append(text).append(' ')
            append(authorName).append(' ')
            append(authorHandle).append(' ')
            quoted?.let { append(it.text).append(' ').append(it.name).append(' ').append(it.handle) }
        }.lowercase()
        return words.all { word -> haystack.contains(word.removePrefix("@")) }
    }
}
