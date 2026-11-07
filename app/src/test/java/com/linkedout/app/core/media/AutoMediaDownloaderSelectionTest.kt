package com.linkedout.app.core.media

import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The first pass's selection, the cases of MTGA's test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/AutoMediaDownloaderSelectionTest.kt#AutoMediaDownloaderSelectionTest
 */
class AutoMediaDownloaderSelectionTest {

    @Test
    fun `takes only what is newer than the watermark`() {
        val posts = listOf(post("c", 300), post("b", 200), post("a", 100))

        val picked = AutoMediaDownloader.selectFresh(posts, since = 150, max = 10)

        assertEquals(listOf("b", "c"), picked.map { it.id })
    }

    @Test
    fun `the first pass takes the newest, not the oldest`() {
        val posts = listOf(post("d", 400), post("c", 300), post("b", 200), post("a", 100))

        val picked = AutoMediaDownloader.selectFresh(posts, since = 0, max = 2)

        assertEquals(listOf("c", "d"), picked.map { it.id })
    }

    @Test
    fun `later passes go oldest first, so a cap leaves the newest for next time`() {
        val posts = listOf(post("d", 400), post("c", 300), post("b", 200), post("a", 100))

        val picked = AutoMediaDownloader.selectFresh(posts, since = 50, max = 2)

        assertEquals(listOf("a", "b"), picked.map { it.id })
    }

    @Test
    fun `the next pass continues where the capped one stopped`() {
        val posts = listOf(post("d", 400), post("c", 300), post("b", 200), post("a", 100))

        val first = AutoMediaDownloader.selectFresh(posts, since = 50, max = 2)
        val second = AutoMediaDownloader.selectFresh(posts, since = first.last().publishedAtMillis, max = 2)

        assertEquals(listOf("c", "d"), second.map { it.id })
    }

    @Test
    fun `skips posts with no media`() {
        val posts = listOf(post("b", 200), post("a", 100, media = false))

        assertEquals(listOf("b"), AutoMediaDownloader.selectFresh(posts, 1, 10).map { it.id })
    }

    @Test
    fun `skips a pin, however old it is`() {
        val posts = listOf(post("pin", 50, pinned = true), post("a", 100))

        assertEquals(listOf("a"), AutoMediaDownloader.selectFresh(posts, 1, 10).map { it.id })
    }

    @Test
    fun `a watermark ahead of the whole feed is read as a first pass`() {
        val posts = listOf(post("b", 200), post("a", 100))

        // Only a stamp from the clock can sit ahead of every post.
        val picked = AutoMediaDownloader.selectFresh(posts, since = 9_999, max = 10)

        assertEquals(listOf("a", "b"), picked.map { it.id })
    }

    @Test
    fun `a watermark on the newest post is not ahead of it`() {
        val posts = listOf(post("b", 200), post("a", 100))

        assertTrue(AutoMediaDownloader.selectFresh(posts, since = 200, max = 10).isEmpty())
    }

    @Test
    fun `nothing new means nothing queued`() {
        val posts = listOf(post("b", 200), post("a", 100))

        assertTrue(AutoMediaDownloader.selectFresh(posts, since = 200, max = 10).isEmpty())
    }

    private fun post(
        id: String,
        millis: Long,
        media: Boolean = true,
        pinned: Boolean = false
    ) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$id",
        isPinned = pinned,
        media = if (media) {
            listOf(MediaItem("https://p/$id.jpg", "https://p/$id.jpg", MediaType.PHOTO))
        } else {
            emptyList()
        }
    )
}
