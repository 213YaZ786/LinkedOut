package com.linkedout.app.data.marks

import com.linkedout.app.core.model.JobCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Saved offers: the toggle, the order and the file, in a temporary folder as JUnit 4's recipe gives it:
 * https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html (a @Rule, `newFile`, `root`).
 */
class SavedJobsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 0L

    // Unconfined: the write runs inside toggle, so the file is there when the test reads it.
    private fun store() = SavedJobs(folder.root, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { ++clock }

    private fun card(id: String) = JobCard(
        id = id,
        title = "Title $id",
        company = "Company",
        location = "Lyon",
        logoUrl = "https://media.licdn.com/logo/$id",
        url = "https://www.linkedin.com/jobs/view/$id"
    )

    @Test
    fun `a first tap saves, a second removes`() {
        val jobs = store()

        jobs.toggle(card("1"))
        assertTrue(jobs.saved.value.isSaved("1"))

        jobs.toggle(card("1"))
        assertFalse(jobs.saved.value.isSaved("1"))
        assertEquals(emptyList<SavedJob>(), jobs.saved.value.jobs)
    }

    @Test
    fun `the newest is first`() {
        val jobs = store()

        jobs.toggle(card("1"))
        jobs.toggle(card("2"))
        jobs.toggle(card("3"))

        assertEquals(listOf("3", "2", "1"), jobs.saved.value.jobs.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), jobs.saved.value.jobs.map { it.savedAt })
    }

    @Test
    fun `a new store reads back what was saved, with what the list needs to reopen it`() {
        store().apply {
            toggle(card("1"))
            toggle(card("2"))
            toggle(card("1"))
        }

        val reopened = store().saved.value.jobs

        assertEquals(listOf("2"), reopened.map { it.id })
        assertEquals(card("2"), reopened.single().toCard())
    }

    @Test
    fun `an unreadable file is an empty list`() {
        folder.newFile("saved_jobs.json").writeText("not json")

        assertEquals(emptyList<SavedJob>(), store().saved.value.jobs)
    }
}
