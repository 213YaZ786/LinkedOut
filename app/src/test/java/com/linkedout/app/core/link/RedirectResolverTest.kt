package com.linkedout.app.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RedirectResolverTest {

    @Test
    fun `reads the address out of Google's redirect notice`() {
        // The shape Google answered a coded /url link with, October 2026.
        val notice = """La page que vous consultiez essaie de vous rediriger vers <a href="/goto?url=CAESYgHrOzAV">""" +
            """https://fr.linkedin.com/company/chu-nantes?a=1&amp;b=2</a>.<br><br>"""
        assertEquals("https://fr.linkedin.com/company/chu-nantes?a=1&b=2", RedirectResolver.noticeTarget(notice))
    }

    @Test
    fun `another page names nothing`() {
        assertNull(RedirectResolver.noticeTarget("""<a href="/search?q=x">https://example.org</a>"""))
    }
}
