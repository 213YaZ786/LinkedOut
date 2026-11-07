package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.ExperienceLevelFilters
import com.linkedout.app.core.model.JobFilters
import com.linkedout.app.core.model.OnSiteOrRemoteFilters
import com.linkedout.app.core.model.TimeFilters
import com.linkedout.app.core.model.TypeFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The parameters of a search, each filter to the value LinkedIn takes (f_TPR, f_WT, f_E, f_JT, f_AL, f_EA), as
 * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#_build_params
 * builds them, and the values of
 * https://github.com/spinlud/py-linkedin-jobs-scraper/blob/master/linkedin_jobs_scraper/filters/filters.py
 */
class JobFiltersParamsTest {

    private fun params(filters: JobFilters, start: Int = 0) = _build_params(
        "engineer", "France", filters.date_posted, filters.workplace, filters.experience, filters.job_type,
        start, filters.easy_apply, filters.under_10_applicants
    )

    @Test
    fun `any adds nothing`() {
        assertEquals(
            listOf("start" to "0", "keywords" to "engineer", "location" to "France"),
            params(JobFilters())
        )
    }

    @Test
    fun `each filter goes to its value`() {
        assertEquals("r86400", params(JobFilters(date_posted = TimeFilters.DAY)).toMap()["f_TPR"])
        assertEquals("r604800", params(JobFilters(date_posted = TimeFilters.WEEK)).toMap()["f_TPR"])
        assertEquals("r2592000", params(JobFilters(date_posted = TimeFilters.MONTH)).toMap()["f_TPR"])

        assertEquals("1", params(JobFilters(workplace = OnSiteOrRemoteFilters.ON_SITE)).toMap()["f_WT"])
        assertEquals("2", params(JobFilters(workplace = OnSiteOrRemoteFilters.REMOTE)).toMap()["f_WT"])
        assertEquals("3", params(JobFilters(workplace = OnSiteOrRemoteFilters.HYBRID)).toMap()["f_WT"])

        val experience = mapOf(
            ExperienceLevelFilters.INTERNSHIP to "1",
            ExperienceLevelFilters.ENTRY_LEVEL to "2",
            ExperienceLevelFilters.ASSOCIATE to "3",
            ExperienceLevelFilters.MID_SENIOR to "4",
            ExperienceLevelFilters.DIRECTOR to "5",
            ExperienceLevelFilters.EXECUTIVE to "6"
        )
        experience.forEach { (choice, value) ->
            assertEquals(value, params(JobFilters(experience = choice)).toMap()["f_E"])
        }

        val jobType = mapOf(
            TypeFilters.FULL_TIME to "F",
            TypeFilters.PART_TIME to "P",
            TypeFilters.CONTRACT to "C",
            TypeFilters.TEMPORARY to "T",
            TypeFilters.INTERNSHIP to "I"
        )
        jobType.forEach { (choice, value) ->
            assertEquals(value, params(JobFilters(job_type = choice)).toMap()["f_JT"])
        }
    }

    @Test
    fun `the two switches are sent as true, and only when on`() {
        val on = params(JobFilters(easy_apply = true, under_10_applicants = true)).toMap()
        assertEquals("true", on["f_AL"])
        assertEquals("true", on["f_EA"])

        val off = params(JobFilters()).toMap()
        assertFalse("f_AL" in off)
        assertFalse("f_EA" in off)
    }

    @Test
    fun `start is the row offset, and no salary and no sort are ever sent`() {
        val all = params(
            JobFilters(
                TimeFilters.WEEK, OnSiteOrRemoteFilters.REMOTE, ExperienceLevelFilters.ENTRY_LEVEL, TypeFilters.FULL_TIME,
                easy_apply = true, under_10_applicants = true
            ),
            start = 60
        ).toMap()

        assertEquals("60", all["start"])
        assertFalse("f_SB2" in all)
        assertFalse("sortBy" in all)
        assertEquals(setOf("start", "keywords", "location", "f_TPR", "f_WT", "f_E", "f_JT", "f_AL", "f_EA"), all.keys)
    }

    @Test
    fun `a blank word or place is not sent, and the filters are counted`() {
        val names = _build_params(" ", "", TimeFilters.ANY, OnSiteOrRemoteFilters.ANY, ExperienceLevelFilters.ANY, TypeFilters.ANY, 0)
            .map { it.first }
        assertEquals(listOf("start"), names)

        assertEquals(0, JobFilters().count)
        assertEquals(3, JobFilters(TimeFilters.DAY, easy_apply = true, under_10_applicants = true).count)
    }
}
