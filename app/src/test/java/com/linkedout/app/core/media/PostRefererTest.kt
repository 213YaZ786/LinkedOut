package com.linkedout.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cases follow Signal-Android's tests of the same kind of check
 * (https://github.com/signalapp/Signal-Android,
 * app/src/test/java/org/thoughtcrime/securesms/util/LinkUtilTest_isValidPreviewUrl.kt
 * and LinkUtilTest_isLegal.java): one list of good addresses, one of bad
 * ones (other hosts, other schemes, hiding places, empty), with the header
 * rules of DownloadManager.Request.addRequestHeader added
 * (https://developer.android.com/reference/android/app/DownloadManager.Request#addRequestHeader(java.lang.String,%20java.lang.String)).
 */
class PostRefererTest {

    private val own = "https://www.linkedin.com/posts/someone_activity-123-abcd"

    @Test
    fun `an own https post address is sent as written`() {
        assertEquals(own, PostReferer.forPermalink(own))
        listOf("linkedin.com", "fr.linkedin.com", "www.linkedin.com").forEach { host ->
            val url = "https://$host/posts/someone_activity-123-abcd"
            assertEquals(url, PostReferer.forPermalink(url))
        }
    }

    @Test
    fun `an upper case scheme and host are accepted`() {
        assertEquals(
            "https://WWW.LinkedIn.com/posts/a",
            PostReferer.forPermalink("HTTPS://WWW.LinkedIn.com/posts/a")
        )
    }

    @Test
    fun `tracking parameters and the fragment are dropped`() {
        assertEquals(own, PostReferer.forPermalink("$own?utm_source=share&utm_medium=android#top"))
    }

    @Test
    fun `another host sets nothing`() {
        assertNull(PostReferer.forPermalink("https://example.org/a"))
        assertNull(PostReferer.forPermalink("https://linkedin.com.example.org/a"))
        assertNull(PostReferer.forPermalink("https://notlinkedin.com/a"))
        assertNull(PostReferer.forPermalink("https://lnkd.in/abc"))
        assertNull(PostReferer.forPermalink("https://media.licdn.com/a.jpg"))
    }

    @Test
    fun `an own host hidden in the user part or the path sets nothing`() {
        assertNull(PostReferer.forPermalink("https://www.linkedin.com@example.org/a"))
        assertNull(PostReferer.forPermalink("https://example.org/www.linkedin.com"))
        assertNull(PostReferer.forPermalink("https://example.org\\@www.linkedin.com/a"))
    }

    @Test
    fun `plain http sets nothing`() {
        assertNull(PostReferer.forPermalink("http://www.linkedin.com/posts/a"))
    }

    @Test
    fun `other schemes set nothing`() {
        assertNull(PostReferer.forPermalink("javascript:alert(1)"))
        assertNull(PostReferer.forPermalink("intent://www.linkedin.com/#Intent;end"))
        assertNull(PostReferer.forPermalink("file:///data/data/x"))
        assertNull(PostReferer.forPermalink("content://www.linkedin.com/a"))
    }

    @Test
    fun `empty, blank or missing sets nothing`() {
        assertNull(PostReferer.forPermalink(null))
        assertNull(PostReferer.forPermalink(""))
        assertNull(PostReferer.forPermalink("   "))
        assertNull(PostReferer.forPermalink("https://"))
    }

    @Test
    fun `a line break or a space in the address sets nothing`() {
        assertNull(PostReferer.forPermalink("$own\r\nX-Evil: 1"))
        assertNull(PostReferer.forPermalink("$own x"))
    }

    @Test
    fun `a very long address sets nothing`() {
        assertNull(PostReferer.forPermalink("$own/" + "a".repeat(PostReferer.MAX_LENGTH)))
        assertNull(PostReferer.forPermalink("$own?q=" + "a".repeat(100_000)))
    }

    @Test
    fun `an address just under the cap is kept`() {
        val path = "a".repeat(PostReferer.MAX_LENGTH - "$own/".length)
        assertEquals("$own/$path", PostReferer.forPermalink("$own/$path"))
    }

    @Test
    fun `a redirect wrapper to another host sets nothing`() {
        assertNull(PostReferer.forPermalink("https://www.linkedin.com/redir/redirect?url=https%3A%2F%2Fexample.org%2Fa"))
        assertNull(PostReferer.forPermalink("https://www.google.com/url?q=https%3A%2F%2Fexample.org%2Fa"))
    }
}
