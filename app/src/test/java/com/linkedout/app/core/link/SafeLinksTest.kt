package com.linkedout.app.core.link

import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases follow Signal-Android's tests of the same check
 * (https://github.com/signalapp/Signal-Android,
 * app/src/test/java/org/thoughtcrime/securesms/util/LinkUtilTest_isValidPreviewUrl.kt
 * and LinkUtilTest_isLegal.java): one list of good addresses, one of bad
 * ones (other schemes, no scheme, hiding characters), with the schemes of
 * Android's guidance on unsafe deep links added
 * (https://developer.android.com/privacy-and-security/risks/unsafe-use-of-deeplinks).
 */
class SafeLinksTest {

    @Test
    fun `http and https are accepted as written`() {
        assertEquals("https://example.org/a?b=1#c", SafeLinks.openable("https://example.org/a?b=1#c"))
        assertEquals("http://example.org", SafeLinks.openable("http://example.org"))
        assertEquals("https://[::1]:8080/x", SafeLinks.openable("https://[::1]:8080/x"))
    }

    @Test
    fun `an upper case scheme is accepted and written in lower case`() {
        assertEquals("https://Example.org/A", SafeLinks.openable("HTTPS://Example.org/A"))
        assertEquals("http://example.org", SafeLinks.openable("HtTp://example.org"))
    }

    @Test
    fun `spaces around the address are dropped`() {
        assertEquals("https://example.org", SafeLinks.openable("  https://example.org \n"))
    }

    @Test
    fun `every other scheme stays text`() {
        listOf(
            "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end",
            "intent:#Intent;action=android.intent.action.CALL;data=tel:123;end",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "file:///data/data/com.example/files/accounts.json",
            "content://com.android.contacts/contacts",
            "market://details?id=com.example",
            "android-app://com.example",
            "tel:+33123456789",
            "mailto:a@example.org",
            "data:text/html;base64,PHNjcmlwdD4=",
            "ftp://example.org/file",
            "bsky://profile/someone",
            "myapp://open?u=https://example.org"
        ).forEach { assertNull(it, SafeLinks.openable(it)) }
    }

    @Test
    fun `no scheme, no host, empty and null`() {
        assertNull(SafeLinks.openable("example.org/path"))
        assertNull(SafeLinks.openable("//example.org/path"))
        assertNull(SafeLinks.openable("https:example.org"))
        assertNull(SafeLinks.openable("https:/example.org"))
        assertNull(SafeLinks.openable("https:\\\\example.org"))
        assertNull(SafeLinks.openable("https://"))
        assertNull(SafeLinks.openable("https:///example.org"))
        assertNull(SafeLinks.openable("https://user@/path"))
        assertNull(SafeLinks.openable("https://:80/path"))
        assertNull(SafeLinks.openable(":https://example.org"))
        assertNull(SafeLinks.openable(""))
        assertNull(SafeLinks.openable("   "))
        assertNull(SafeLinks.openable(null))
    }

    @Test
    fun `whitespace and invisible characters inside hide nothing`() {
        listOf(
            "java\tscript:alert(1)",
            "java\nscript:alert(1)",
            " javascript:alert(1)",
            "\u0000javascript:alert(1)",
            "jav​ascript:alert(1)",
            "https ://example.org",
            "https://exa mple.org",
            "https://example.org/a b",
            "https://example.org\r\nHost: evil.example",
            "https://example.org‮/gpj.exe",
            "https://example.org​",
            " javascript:alert(1)",
            "http://example.org\u0007"
        ).forEach { assertNull(it, SafeLinks.openable(it)) }
    }

    @Test
    fun `a scheme only hidden inside the address does not count`() {
        assertEquals("https://example.org/?u=javascript:alert(1)", SafeLinks.openable("https://example.org/?u=javascript:alert(1)"))
        assertNull(SafeLinks.openable("javascript://example.org/%0Aalert(1)"))
        assertFalse(SafeLinks.isOpenable("intent://example.org#Intent;end"))
        assertTrue(SafeLinks.isOpenable("https://example.org"))
    }

    private class Recorder : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    @Test
    fun `the handler opens a web link, hands an app link to the app, refuses the rest`() {
        val platform = Recorder()
        val inApp = mutableListOf<String>()
        var refused = 0
        val handler = SafeUriHandler(
            platform = platform,
            inApp = { if (it.startsWith("at://")) { inApp += it; true } else false },
            onRefused = { refused++ }
        )
        handler.openUri("HTTPS://example.org/x")
        handler.openUri("at://did:plc:abc/app.bsky.feed.post/1")
        handler.openUri("intent:#Intent;action=android.intent.action.CALL;data=tel:123;end")
        handler.openUri("file:///data/data/x/files/a.json")
        handler.openUri("javascript:alert(1)")
        assertEquals(listOf("https://example.org/x"), platform.opened)
        assertEquals(listOf("at://did:plc:abc/app.bsky.feed.post/1"), inApp)
        assertEquals(3, refused)
    }

    // The same Recorder as above (Compose's UriHandler, https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/UriHandler).
    @Test
    fun `the intermediary gets web links before the platform, the app's own links never`() {
        val platform = Recorder()
        val asked = mutableListOf<String>()
        var refused = 0
        val handler = SafeUriHandler(
            platform = platform,
            inApp = { it.startsWith("at://") },
            onRefused = { refused++ },
            intermediary = { asked += it }
        )
        handler.openUri("HTTPS://example.org/x")
        handler.openUri("at://did:plc:abc/app.bsky.feed.post/1")
        handler.openUri("javascript:alert(1)")
        assertEquals(listOf("https://example.org/x"), asked)
        assertEquals(emptyList<String>(), platform.opened)
        assertEquals(1, refused)
        // What the sheet's Open chose goes out, checked again.
        handler.openOut("https://example.org/x")
        handler.openOut("intent:#Intent;end")
        assertEquals(listOf("https://example.org/x"), platform.opened)
        assertEquals(2, refused)
    }

    @Test
    fun `a link no app can open is refused, not thrown`() {
        var refused = 0
        val handler = SafeUriHandler(
            platform = object : UriHandler {
                override fun openUri(uri: String) = throw IllegalArgumentException("Can't open $uri")
            },
            inApp = { false },
            onRefused = { refused++ }
        )
        handler.openUri("https://example.org")
        assertEquals(1, refused)
    }
}
