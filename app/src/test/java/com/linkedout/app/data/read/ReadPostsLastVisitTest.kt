package com.linkedout.app.data.read

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The previous visit decides what is new. MTGA has no test of its ReadMarks, so the cases follow its comments:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/data/read/ReadMarks.kt
 * The folder is JUnit 4's TemporaryFolder rule, as SavedJobsTest uses it.
 */
class ReadPostsLastVisitTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val visit = 1_000L

    // Unconfined: the file is read inside the constructor, so the state is ready when the test asks.
    private fun store(previousVisit: Long? = visit): ReadPosts {
        if (previousVisit != null) folder.root.resolve("last-visit.txt").writeText(previousVisit.toString())
        return ReadPosts(folder.root, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    }

    @Test
    fun `a post published before the previous visit is read`() = runBlocking {
        val reads = store()
        reads.markReadBeforeLastVisit(emptyMap())

        reads.markReadIfOld(listOf("old" to visit - 1))

        assertFalse(reads.state.value.isUnread("old"))
    }

    @Test
    fun `a post published after the previous visit is not`() = runBlocking {
        val reads = store()
        reads.markReadBeforeLastVisit(emptyMap())

        reads.markReadIfOld(listOf("new" to visit + 1))

        assertTrue(reads.state.value.isUnread("new"))
    }

    @Test
    fun `at launch only the old ones are marked`() = runBlocking {
        val reads = store()

        reads.markReadBeforeLastVisit(mapOf("old" to visit - 1, "same" to visit, "new" to visit + 1))

        assertFalse(reads.state.value.isUnread("old"))
        assertFalse(reads.state.value.isUnread("same"))
        assertTrue(reads.state.value.isUnread("new"))
    }

    @Test
    fun `the first launch has no previous visit and nothing is new`() = runBlocking {
        val reads = store(previousVisit = null)

        reads.markReadBeforeLastVisit(mapOf("a" to 5L, "b" to Long.MAX_VALUE - 1))

        assertFalse(reads.state.value.isUnread("a"))
        assertFalse(reads.state.value.isUnread("b"))
    }

    @Test
    fun `the launch writes this visit for the next one`() = runBlocking {
        val reads = store()

        reads.markReadBeforeLastVisit(emptyMap())

        val written = folder.root.resolve("last-visit.txt").readText().trim().toLong()
        assertTrue(written > visit)
    }
}
