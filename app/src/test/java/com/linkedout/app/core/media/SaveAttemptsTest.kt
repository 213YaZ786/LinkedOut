package com.linkedout.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Files under JUnit's TemporaryFolder rule, as SaveOriginTest does
 * (https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html), and the cases of MTGA's test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/SaveAttemptsTest.kt#SaveAttemptsTest
 */
class SaveAttemptsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private fun attempts() = SaveAttempts(File(folder.root, "save-attempts.json")) { now }

    @Test
    fun `a name asked for is recent, another is not`() {
        val attempts = attempts()
        attempts.add(listOf("1.0.jpg"))

        assertTrue(attempts.recently("1.0.jpg"))
        assertFalse(attempts.recently("2.0.jpg"))
    }

    @Test
    fun `a name is asked for again after the backoff`() {
        val attempts = attempts()
        attempts.add(listOf("1.0.jpg"))
        now += SaveAttempts.BACKOFF_MILLIS + 1

        assertFalse(attempts.recently("1.0.jpg"))
    }

    @Test
    fun `the notes survive a restart`() {
        attempts().add(listOf("1.0.jpg"))

        assertTrue(attempts().recently("1.0.jpg"))
    }

    @Test
    fun `a damaged file asks for nothing less`() {
        File(folder.root, "save-attempts.json").writeText("{ broken")

        assertFalse(attempts().recently("1.0.jpg"))
    }

    @Test
    fun `the oldest notes go past the cap`() {
        val attempts = attempts()
        attempts.add(listOf("first"))
        now += 1
        attempts.add((1..SaveAttempts.MAX_ENTRIES).map { "n$it" })

        assertFalse(attempts.recently("first"))
        assertTrue(attempts.recently("n${SaveAttempts.MAX_ENTRIES}"))
    }
}
