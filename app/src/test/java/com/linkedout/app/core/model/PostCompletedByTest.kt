package com.linkedout.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A feed card completed by the post's own page when the post is opened. The
 * cases are the ones seen on real pages in October 2026: a carousel the
 * profile shows one picture of, a profile video with no source, a document
 * the profile does not show, and a company card carrying a document's cover.
 */
class PostCompletedByTest {

    @Test
    fun `a carousel gets every picture`() {
        val card = post(media = photos(1))
        val page = post(media = photos(6))

        assertEquals(6, card.completedBy(page).media.size)
    }

    @Test
    fun `a video cover gets its source`() {
        val cover = MediaItem("cover", "cover", MediaType.VIDEO, playable = false, sourcePostId = ID)
        val video = MediaItem("cover", "https://dms.licdn.com/playlist/vid/v.mp4", MediaType.VIDEO)
        val completed = post(media = listOf(cover)).completedBy(post(media = listOf(video)))

        assertEquals(listOf(video), completed.media)
    }

    @Test
    fun `a missing document is added`() {
        val completed = post().completedBy(post(document = DOCUMENT))

        assertEquals(DOCUMENT, completed.document)
    }

    @Test
    fun `a document cover is not shown twice`() {
        val cover = photos(1)
        val completed = post(media = cover).completedBy(post(document = DOCUMENT))

        assertEquals(DOCUMENT, completed.document)
        assertEquals(emptyList<MediaItem>(), completed.media)
    }

    @Test
    fun `a page that shows less changes nothing`() {
        val card = post(media = photos(3))

        assertSame(card, card.completedBy(post(media = photos(2))))
        assertSame(card, card.completedBy(post(media = emptyList())))
        assertSame(card, card.completedBy(post(media = photos(3))))
    }

    @Test
    fun `only media and document are taken`() {
        val card = post(media = photos(1)).copy(text = "card text", stats = PostStats(likes = 5))
        val page = post(media = photos(4)).copy(text = "page text", stats = PostStats(likes = 9))
        val completed = card.completedBy(page)

        assertEquals("card text", completed.text)
        assertEquals(5, completed.stats?.likes)
    }

    @Test
    fun `another post changes nothing`() {
        val card = post(media = photos(1))

        assertSame(card, card.completedBy(post(media = photos(6)).copy(id = "1")))
    }

    private fun post(media: List<MediaItem> = emptyList(), document: SharedDocument? = null) = Post(
        id = ID,
        authorHandle = "someone",
        authorName = "Someone",
        text = "",
        publishedAtMillis = 0,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$ID",
        media = media,
        document = document
    )

    private fun photos(count: Int) = List(count) { MediaItem("p$it", "p$it", MediaType.PHOTO) }

    private companion object {
        const val ID = "7510767969715539969"
        val DOCUMENT = SharedDocument(title = "Slides", manifestUrl = "https://media.licdn.com/dms/document/m")
    }
}
