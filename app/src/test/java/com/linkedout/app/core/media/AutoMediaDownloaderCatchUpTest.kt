package com.linkedout.app.core.media

import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.settings.AutoDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catch-up pass's pure parts, in the steps of AntennaPod's automatic
 * download algorithm (gate, candidates, drop, cap):
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/autodownload/AutomaticDownloadAlgorithm.java#autoDownloadUndownloadedItems
 * and the cases of MTGA's test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/AutoMediaDownloaderCatchUpTest.kt#AutoMediaDownloaderCatchUpTest
 */
class AutoMediaDownloaderCatchUpTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    /** The candidates, the drop and the cap, in AntennaPod's order. */
    private fun select(
        posts: List<Post>,
        keepDays: Int = 0,
        cacheSize: Int = 40,
        downloaded: Int = 0,
        have: (String, Int, Boolean) -> Boolean = { _, _, _ -> false },
        asked: (String) -> Boolean = { false },
        known: (String) -> String? = { null }
    ): List<AutoMediaDownloader.Want> {
        val candidates = AutoMediaDownloader.autoDownloadCandidates(posts, now, keepDays)
        val missing = AutoMediaDownloader.dropDownloaded(candidates, have, asked, known)
        val episodeSpaceLeft = AutoMediaDownloader.episodeSpaceLeft(cacheSize, downloaded, missing.size)
        return missing.subList(0, episodeSpaceLeft)
    }

    @Test
    fun `newest posts first`() {
        val posts = listOf(photoPost("a", 10 * day), photoPost("c", 30 * day), photoPost("b", 20 * day))

        assertEquals(listOf("c", "b", "a"), select(posts).map { it.post.id })
    }

    @Test
    fun `stops at the cap, mid post if need be`() {
        val posts = listOf(photoPost("c", 30 * day, photos = 3), photoPost("b", 20 * day))

        val picked = select(posts, cacheSize = 2)

        assertEquals(listOf("c" to 0, "c" to 1), picked.map { it.post.id to it.position })
    }

    @Test
    fun `the cap counts the stills too`() {
        val posts = listOf(videoPost("v", 30 * day), photoPost("p", 20 * day))

        val picked = select(posts, cacheSize = 2)

        assertEquals(listOf(true, false), picked.map { it.still })
        assertEquals(listOf("v", "v"), picked.map { it.post.id })
    }

    @Test
    fun `the cap counts what the first pass queued`() {
        val posts = listOf(photoPost("c", 30 * day, photos = 3))

        val picked = select(posts, cacheSize = 3, downloaded = 2)

        assertEquals(listOf(0), picked.map { it.position })
    }

    @Test
    fun `space left is all the candidates when they fit`() {
        assertEquals(5, AutoMediaDownloader.episodeSpaceLeft(40, 10, 5))
        assertEquals(30, AutoMediaDownloader.episodeSpaceLeft(40, 10, 50))
    }

    @Test
    fun `space left never goes below zero`() {
        assertEquals(0, AutoMediaDownloader.episodeSpaceLeft(40, 45, 5))
    }

    @Test
    fun `a video's still comes before the video`() {
        val picked = select(listOf(videoPost("v", 30 * day)))

        assertEquals(listOf(true, false), picked.map { it.still })
    }

    @Test
    fun `a post older than the retention is not kept, so not saved`() {
        val posts = listOf(photoPost("new", 95 * day), photoPost("old", 80 * day))

        assertEquals(listOf("new"), select(posts, keepDays = 10).map { it.post.id })
    }

    @Test
    fun `zero days of retention keeps everything`() {
        val posts = listOf(photoPost("old", 1 * day))

        assertEquals(listOf("old"), select(posts, keepDays = 0).map { it.post.id })
    }

    @Test
    fun `what is on the phone is not asked for again`() {
        val posts = listOf(photoPost("a", 30 * day, photos = 2))

        val picked = select(posts, have = { _, position, _ -> position == 0 })

        assertEquals(listOf(1), picked.map { it.position })
    }

    @Test
    fun `a video with its still but no file wants the file only`() {
        val picked = select(listOf(videoPost("v", 30 * day)), have = { _, _, still -> still })

        assertEquals(listOf(false), picked.map { it.still })
    }

    @Test
    fun `a file asked for lately is left out`() {
        val post = photoPost("a", 30 * day)
        val name = OfflineMedia.name("a", "someone", 0, post.media[0])

        assertTrue(select(listOf(post), asked = { it == name }).isEmpty())
    }

    @Test
    fun `a playlist is not saved but its still is`() {
        val post = videoPost("v", 30 * day, url = "https://dms.licdn.com/playlist/vid/abc.m3u8?e=1")

        assertEquals(listOf(true), select(listOf(post)).map { it.still })
    }

    @Test
    fun `a video with no still address wants the file only`() {
        val post = videoPost("v", 30 * day, preview = "")

        assertEquals(listOf(false), select(listOf(post)).map { it.still })
    }

    @Test
    fun `a profile video without an address wants its still, and the file once its address is known`() {
        val post = coverPost("v", 30 * day)

        assertEquals(listOf(true), select(listOf(post)).map { it.still })
        assertEquals(listOf(true, false), select(listOf(post), known = { "https://dms.licdn.com/v.mp4" }).map { it.still })
    }

    @Test
    fun `posts without media give nothing`() {
        assertTrue(select(listOf(photoPost("a", 30 * day, photos = 0))).isEmpty())
    }

    @Test
    fun `thousands of posts are cut at the cap`() {
        val posts = (1..5_000).map { photoPost("p$it", it * 1_000L) }

        val picked = select(posts, cacheSize = AutoMediaDownloader.MAX_CATCH_UP_FILES)

        assertEquals(40, picked.size)
        assertEquals("p5000", picked.first().post.id)
    }

    @Test
    fun `the network rule`() {
        assertFalse(AutoMediaDownloader.allows(AutoDownload.NEVER, metered = false))
        assertFalse(AutoMediaDownloader.allows(AutoDownload.NEVER, metered = true))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.WIFI, metered = false))
        assertFalse(AutoMediaDownloader.allows(AutoDownload.WIFI, metered = true))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.ALWAYS, metered = false))
        assertTrue(AutoMediaDownloader.allows(AutoDownload.ALWAYS, metered = true))
    }

    @Test
    fun `the power rule, charging or not on mobile data`() {
        assertTrue(AutoMediaDownloader.powerAllows(charging = true, metered = true))
        assertTrue(AutoMediaDownloader.powerAllows(charging = true, metered = false))
        assertTrue(AutoMediaDownloader.powerAllows(charging = false, metered = false))
        assertFalse(AutoMediaDownloader.powerAllows(charging = false, metered = true))
    }

    @Test
    fun `an m3u8 address is a playlist, an mp4 is not`() {
        assertTrue(MediaDownloader.isHls("https://dms.licdn.com/a/pl/x.m3u8?e=1"))
        assertFalse(MediaDownloader.isHls("https://dms.licdn.com/a/vid/x.mp4?e=1"))
    }

    private fun photoPost(id: String, millis: Long, photos: Int = 1) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$id",
        media = (0 until photos).map { MediaItem("https://p/$id-$it.jpg", "https://p/$id-$it.jpg", MediaType.PHOTO) }
    )

    private fun videoPost(
        id: String,
        millis: Long,
        url: String = "https://v/$id.mp4",
        preview: String = "https://p/$id.jpg"
    ) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$id",
        media = listOf(MediaItem(preview, url, MediaType.VIDEO))
    )

    /** A video as a profile page names it: the cover as the address, not playable. */
    private fun coverPost(id: String, millis: Long) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = millis,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$id",
        media = listOf(
            MediaItem("https://p/$id.jpg", "https://p/$id.jpg", MediaType.VIDEO, playable = false, sourcePostId = id)
        )
    )
}
