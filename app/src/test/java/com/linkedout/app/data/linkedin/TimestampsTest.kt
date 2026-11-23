package com.linkedout.app.data.linkedin

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An activity id carries its creation time in its first 41 bits. The ten ids are the posts of CHU Nantes' page
 * as the page lists them, newest first. Ported from
 * https://github.com/Ollie-Boyd/Linkedin-post-timestamp-extractor/blob/main/extractor.py#get_date_from_linkedin_activity
 * (the formatting of the date is not ported: the test compares the instants).
 */
class TimestampsTest {

    private val linkedin_ids = listOf(
        7530570425500753920L,
        7529428497375711232L,
        7529148568394174464L,
        7528707047719247872L,
        7528422820272488450L,
        7528125200517894145L,
        7526989932851257344L,
        7526577282170298368L,
        7525823301311488000L,
        7525469724768354304L,
    )

    @Test
    fun `an id gives the instant of its first 41 bits`() {
        for (linkedin_id in linkedin_ids) {
            assertEquals(linkedin_id shr 22, Timestamps.get_date_from_linkedin_activity(linkedin_id.toString()))
        }
    }

    @Test
    fun `three ids give the instants the page was checked against`() {
        assertEquals(
            Instant.parse("2026-11-23T09:58:20.672Z").toEpochMilli(),
            Timestamps.fromActivityId("7530570425500753920"),
        )
        assertEquals(
            Instant.parse("2026-11-20T06:20:43.791Z").toEpochMilli(),
            Timestamps.fromActivityId("7529428497375711232"),
        )
        assertEquals(
            Instant.parse("2026-11-09T08:09:58.851Z").toEpochMilli(),
            Timestamps.fromActivityId("7525469724768354304"),
        )
    }

    @Test
    fun `the ten ids come out in the order of the ids`() {
        val instants = linkedin_ids.map { Timestamps.fromActivityId(it.toString())!! }
        assertEquals(instants.sortedDescending(), instants)
        assertEquals(instants.size, instants.toSet().size)
        for ((newer, older) in instants.zipWithNext()) assertTrue(newer > older)
    }

    @Test
    fun `the forms of an id are accepted`() {
        val timestamp_ms = Instant.parse("2026-11-23T09:58:20.672Z").toEpochMilli()
        val forms = listOf(
            "7530570425500753920",
            " 7530570425500753920 ",
            "urn:li:activity:7530570425500753920",
            "urn:li:ugcPost:7530570425500753920",
            "urn:li:share:7530570425500753920",
            "chu-nantes_activity-7530570425500753920-AbCd",
            "activity-7530570425500753920",
            "https://www.linkedin.com/feed/update/urn:li:activity:7530570425500753920",
        )
        for (post_url in forms) {
            assertEquals(post_url, timestamp_ms, Timestamps.fromActivityId(post_url.trim()))
        }
    }

    @Test
    fun `a comment id is read the same way`() {
        val timestamp_ms = Instant.parse("2024-09-23T14:38:10.000Z").toEpochMilli()
        val comment = Timestamps.fromActivityId("7243992047441870850")!!
        assertEquals(timestamp_ms / 1000, comment / 1000)
        assertEquals(comment, Timestamps.get_date_from_linkedin_comment("fsd_comment:(7243992047441870850,urn:li:activity:7206271470342131712)"))
    }

    @Test
    fun `garbage and implausible values give null`() {
        assertNull(Timestamps.fromActivityId(null))
        assertNull(Timestamps.fromActivityId(""))
        assertNull(Timestamps.fromActivityId("not an id"))
        assertNull(Timestamps.fromActivityId("urn:li:activity:123"))
        assertNull(Timestamps.fromActivityId("12345678901234567890"))
        // Only 60 bits long, so its first 41 bits are a time in 2030, in the future.
        assertNull(Timestamps.fromActivityId("1000000000000000000"))
        // Decodes to 2037, in the future.
        assertNull(Timestamps.fromActivityId("9000000000000000000"))
    }

    @Test
    fun `a label alone still gives now and an id wins when present`() {
        val now = 1_800_000_000_000L
        assertEquals(now, Timestamps.fromRelative("3 j.", now))
        assertEquals(now - 3 * 24 * 60 * 60 * 1000L, Timestamps.fromRelative("3d", now))
        val id = "urn:li:activity:7530570425500753920"
        val published = Timestamps.fromActivityId(id) ?: Timestamps.fromRelative("3 j.", now)
        assertEquals(7530570425500753920L shr 22, published)
    }
}
