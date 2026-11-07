package com.linkedout.app.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.cache.FeedCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What locks point 2 of requests.md: a saved post's photo, a video's cover and the video resolve to files on the
 * phone with no network, and a stored video that a profile page named without an address becomes playable once the
 * post's page gave it. Run on the JVM under Robolectric for the app's external files folder, with real files, as
 * MTGA's name tests do without the folder:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaNameTest.kt
 * The rule under test is AntennaPod's, the local file when it is available, else the stream:
 * https://github.com/AntennaPod/AntennaPod/blob/develop/playback/base/src/main/java/de/danoeh/antennapod/playback/base/MediaItemAdapter.java#L85-L90
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class SavedPostOfflineTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val photo = MediaItem("https://media.licdn.com/p?e=1&t=a", "https://media.licdn.com/p?e=1&t=a", MediaType.PHOTO)
    private val video = MediaItem("https://media.licdn.com/c?e=1&t=a", "https://dms.licdn.com/v.mp4?e=1&t=a", MediaType.VIDEO)

    private fun save(file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a saved photo, a cover and a video resolve to local files`() = runBlocking {
        val store = OfflineMedia(context)
        save(store.fileFor("42", "SomeOne", 0, photo))
        save(store.stillFor("42", "SomeOne", 1, video))
        save(store.fileFor("42", "SomeOne", 1, video))
        store.refresh()

        val shownPhoto = resolve(store, "42", 0, photo)
        assertTrue(shownPhoto.offline)
        assertEquals(store.fileFor("42", "SomeOne", 0, photo).absolutePath, shownPhoto.playback)
        assertEquals(shownPhoto.playback, shownPhoto.preview)

        val shownVideo = resolve(store, "42", 1, video)
        assertTrue(shownVideo.offline)
        assertEquals(store.fileFor("42", "SomeOne", 1, video).absolutePath, shownVideo.playback)
        assertEquals(store.stillFor("42", "SomeOne", 1, video).absolutePath, shownVideo.preview)
    }

    @Test
    fun `an unsaved video is the network address, and the other address of a saved post still finds its file`() = runBlocking {
        val store = OfflineMedia(context)
        save(store.fileFor("43", "someone", 0, photo))
        store.refresh()

        assertFalse(resolve(store, "43", 1, video).offline)
        assertEquals(video.downloadUrl, resolve(store, "43", 1, video).playback)
        // The post page signs the same picture otherwise: the file is found by post and position.
        val reSigned = photo.copy(previewUrl = "https://media.licdn.com/p?e=2&t=b", downloadUrl = "https://media.licdn.com/p?e=2&t=b")
        assertTrue(resolve(store, "43", 0, reSigned).offline)
    }

    @Test
    fun `files saved before the account folders are deleted at the refresh`() = runBlocking {
        val store = OfflineMedia(context)
        val old = java.io.File(store.fileFor("44", "someone", 0, photo).parentFile!!.parentFile, "0123456789abcdef.jpg")
        save(old)
        store.refresh()

        assertFalse(old.exists())
    }

    @Test
    fun `retention drops the files of a post the cache no longer holds`() = runBlocking {
        val store = OfflineMedia(context)
        save(store.fileFor("45", "someone", 0, photo))
        save(store.fileFor("46", "someone", 0, photo))
        store.refresh()

        store.keepOnly(setOf("45"))

        assertNotNull(store.localPath("45", 0))
        assertNull(store.localPath("46", 0))
    }

    @Test
    fun `a stored video without an address becomes playable after replaceMedia`() = runBlocking {
        val cache = FeedCache(context)
        val cover = MediaItem("https://media.licdn.com/c", "https://media.licdn.com/c", MediaType.VIDEO, playable = false, sourcePostId = "50")
        cache.write(feed("someone", post("50", listOf(cover))))
        val playable = MediaItem("https://media.licdn.com/c2", "https://dms.licdn.com/v.mp4?e=1", MediaType.VIDEO)

        assertTrue(cache.replaceMedia(null, "50", listOf(playable)))

        val stored = cache.read("someone")!!.posts.single().media.single()
        assertTrue(stored.playable)
        assertEquals("https://dms.licdn.com/v.mp4?e=1", stored.downloadUrl)
        // The cover is the one stored, its saved copy is named by position.
        assertEquals("https://media.licdn.com/c", stored.previewUrl)
    }

    @Test
    fun `replaceMedia leaves a playable video and an unknown post alone`() = runBlocking {
        val cache = FeedCache(context)
        cache.write(feed("someone", post("51", listOf(video))))
        val other = MediaItem("https://x/p", "https://x/v.mp4", MediaType.VIDEO)

        assertFalse(cache.replaceMedia(null, "999", listOf(other)))
        assertTrue(cache.replaceMedia("someone", "51", listOf(other)))

        assertEquals(video, cache.read("someone")!!.posts.single().media.single())
    }

    private fun post(id: String, media: List<MediaItem>) = Post(
        id = id,
        authorHandle = "someone",
        authorName = "Someone",
        text = "text",
        publishedAtMillis = 1_000L,
        permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$id",
        media = media
    )

    private fun feed(handle: String, vararg posts: Post) =
        Feed(handle = handle, displayName = handle, posts = posts.toList(), fetchedFromHost = "www.linkedin.com", fetchedAtMillis = 1_000L)
}
