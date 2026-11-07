package com.linkedout.app.core.media

import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The names of the saved files, in the shape of AntennaPod's generator test and of MTGA's name test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaNameTest.kt#OfflineMediaNameTest
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/FilenameGeneratorTest.java
 *
 * LinkedIn's addresses carry no extension and are signed: the name never reads them.
 */
class OfflineMediaNameTest {

    @Test
    fun `a name goes back to the post it came from`() {
        val name = OfflineMedia.name(
            postId = "7123456789012345678",
            authorHandle = "someone",
            index = 0,
            item = video("https://dms.licdn.com/playlist/vid/v2/D4E05AQ/mp4-720p-30fp-crf28/B4E?e=1790000000&v=beta&t=abc")
        )

        assertEquals("7123456789012345678.0.mp4", name)
        assertEquals(
            OfflineMedia.Parsed("someone", "7123456789012345678", 0),
            OfflineMedia.parse(name, folder = OfflineMedia.getMediafilePath("someone"))
        )
    }

    @Test
    fun `one folder per account whatever the case of the handle`() {
        assertEquals("some_one-x", OfflineMedia.getMediafilePath("Some_One-X"))
        assertEquals(OfflineMedia.getMediafilePath("SomeOne"), OfflineMedia.getMediafilePath("someone"))
    }

    @Test
    fun `a photo is a jpg whatever its address says`() {
        val name = OfflineMedia.name("42", "a", 1, photo("https://media.licdn.com/dms/image/v2/D4E22AQ/feedshare-shrink_800/B4E?e=1&v=beta&t=x"))

        assertEquals("42.1.jpg", name)
    }

    @Test
    fun `the same picture under two signed addresses has one name`() {
        val first = OfflineMedia.name("42", "a", 0, photo("https://media.licdn.com/dms/image/x?e=1&t=a"))
        val second = OfflineMedia.name("42", "a", 0, photo("https://media.licdn.com/dms/image/y?e=2&t=b"))

        assertEquals(first, second)
    }

    @Test
    fun `a video and a gif are mp4`() {
        assertEquals("42.2.mp4", OfflineMedia.name("42", "a", 2, video("https://v/x")))
        assertEquals("42.2.mp4", OfflineMedia.name("42", "a", 2, MediaItem("https://p/x", "https://v/x", MediaType.GIF)))
    }

    @Test
    fun `a file the app did not write is ignored`() {
        assertNull(OfflineMedia.parse("holiday.jpg", "a"))
        assertNull(OfflineMedia.parse("42.jpg", "a"))
        assertNull(OfflineMedia.parse("42.notanumber.jpg", "a"))
        assertNull(OfflineMedia.parse(".0.jpg", "a"))
        assertNull(OfflineMedia.parse("42.0.other.jpg", "a"))
    }

    private fun photo(url: String) = MediaItem(url, url, MediaType.PHOTO)

    private fun video(url: String) = MediaItem(url, url, MediaType.VIDEO)
}
