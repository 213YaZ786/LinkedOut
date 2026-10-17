package com.linkedout.app.core.link

import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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

    // Expand's hosts: URLCheck's StatusModule hop (https://github.com/TrianguloY/URLCheck), with the guard of SMS's LinkPreview.public.
    @Test
    fun `expand never reaches this phone, the home network or a link-local address`() {
        listOf("localhost", "127.0.0.1", "0.0.0.0", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "[::1]", "[fc00::1]", "[fe80::1]", "224.0.0.1")
            .forEach { assertFalse(it, RedirectResolver.isPublicHost(it)) }
    }

    @Test
    fun `expand refuses what is not a public web address, without any request`() {
        val resolver = RedirectResolver()
        listOf("http://127.0.0.1/x", "https://192.168.0.1/", "javascript:alert(1)", "file:///data/x", "intent:#Intent;end", "https://exa mple.org")
            .forEach { assertNull(it, runBlocking { resolver.expand(it) }) }
    }
}
