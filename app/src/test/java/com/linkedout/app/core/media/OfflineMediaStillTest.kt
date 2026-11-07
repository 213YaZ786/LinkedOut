package com.linkedout.app.core.media

import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The still's name, in the shape of this app's own name test and of MTGA's:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaStillTest.kt#OfflineMediaStillTest
 */
class OfflineMediaStillTest {

    private val video = MediaItem("https://media.licdn.com/dms/image/v2/D4E05AQ/videocover?e=1&t=a", "https://dms.licdn.com/v.mp4", MediaType.VIDEO)

    @Test
    fun `a still goes back to its post and position`() {
        val name = OfflineMedia.stillName("42", "SomeOne", 1, video)

        assertEquals("42.1.still.jpg", name)
        assertEquals(
            OfflineMedia.Parsed("someone", "42", 1, still = true),
            OfflineMedia.parse(name, OfflineMedia.getMediafilePath("SomeOne"))
        )
    }

    @Test
    fun `the still and the video of one media have different names`() {
        assertEquals("42.1.mp4", OfflineMedia.name("42", "someone", 1, video))
        assertEquals(false, OfflineMedia.parse("42.1.mp4", "someone")?.still)
    }

    @Test
    fun `a third part that is not still is no file of ours`() {
        assertNull(OfflineMedia.parse("42.1.other.jpg", "someone"))
        assertNull(OfflineMedia.parse("42.1.still.extra.jpg", "someone"))
    }
}
