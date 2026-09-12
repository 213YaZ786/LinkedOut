package com.linkedout.app.core.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs one bot check at a time in a real browser engine.
 *
 * The solver itself holds no WebView. It publishes a [Task], and the UI layer
 * ([com.linkedout.app.feature.challenge.ChallengeBackstage]) renders the WebView
 * that works on it. A WebView attached to a live window behaves exactly like a
 * browser, which is what these checks test for. A detached one gets throttled.
 *
 * Consequence stated plainly: with no activity on screen, as during background
 * sync, there is no host and checks fail fast with [Result.NoHost].
 */
class ChallengeSolver(private val session: WebSession) {

    sealed interface Result {
        /** Through. [html] is the real page, [status] its main frame status. */
        data class Cleared(val html: String, val finalUrl: String, val status: Int) : Result

        /** Still a check when time ran out. A person may be able to pass it. */
        data object NeedsInteraction : Result

        /** The host refused the browser too, no check was offered. */
        data class Blocked(val status: Int?) : Result

        /** No window to run a WebView in. */
        data object NoHost : Result

        /** The user closed the check. */
        data object Cancelled : Result

        /** The engine itself failed, for example a renderer crash. */
        data class Failed(val detail: String) : Result
    }

    class Task internal constructor(
        val url: String,
        val host: String,
        val interactive: Boolean
    ) {
        internal val result = CompletableDeferred<Result>()
    }

    private val mutex = Mutex()
    private val hosts = AtomicInteger(0)

    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task.asStateFlow()

    /**
     * Loads [url] and waits until the page is no longer a check. Offscreen
     * attempts are bounded. Interactive ones wait for the user.
     */
    suspend fun solve(url: String, host: String, interactive: Boolean = false): Result {
        val result = mutex.withLock {
            if (hosts.get() == 0) return Result.NoHost
            val task = Task(url, host, interactive)
            _task.value = task
            try {
                if (interactive) {
                    task.result.await()
                } else {
                    withTimeoutOrNull(OFFSCREEN_TIMEOUT_MS) { task.result.await() }
                        ?: Result.NeedsInteraction
                }
            } finally {
                _task.value = null
            }
        }
        if (result is Result.Cleared) session.markCleared(host)
        return result
    }

    /** Called by the WebView driver. The first answer wins, later ones are ignored. */
    fun complete(task: Task, result: Result) {
        task.result.complete(result)
    }

    fun onUserAgent(value: String) = session.onUserAgent(value)

    fun attachHost() {
        hosts.incrementAndGet()
    }

    fun detachHost() {
        if (hosts.decrementAndGet() <= 0) {
            hosts.set(0)
            _task.value?.let { complete(it, Result.NoHost) }
        }
    }

    private companion object {
        /** Anubis at typical difficulty takes a few seconds on a phone. */
        const val OFFSCREEN_TIMEOUT_MS = 20_000L
    }
}
