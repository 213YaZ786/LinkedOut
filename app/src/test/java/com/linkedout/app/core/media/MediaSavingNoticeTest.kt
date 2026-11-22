package com.linkedout.app.core.media

import com.linkedout.app.core.media.MediaSavingNotice.Progress
import com.linkedout.app.core.media.MediaSavingNotice.Shade
import com.linkedout.app.core.media.MediaSavingNotice.Tally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSavingNoticeTest {

    @Test
    fun `running counts what is over, paused included as not over`() {
        val progress = Progress.running(Tally(running = 3, saved = 8, failed = 1))

        assertEquals("9 of 12", progress.text)
        assertFalse(progress.done)
    }

    @Test
    fun `the end says what failed`() {
        assertEquals("10 files saved, 2 files failed", Progress.finished(Tally(saved = 10, failed = 2)).text)
        assertEquals("1 file saved", Progress.finished(Tally(saved = 1)).text)
    }

    @Test
    fun `nothing saved is not announced as saved`() {
        val progress = Progress.finished(Tally(failed = 3))

        assertEquals("Media could not be saved", progress.title)
        assertTrue(progress.done)
    }

    // The rule of AntennaPod's EpisodeDownloadWorker.sendErrorNotification
    // (https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service/src/main/java/de/danoeh/antennapod/net/download/service/episode/EpisodeDownloadWorker.java#sendErrorNotification):
    // the app on screen takes the message, the shade only when it is not.

    @Test
    fun `on screen the shade shows nothing, running or ended`() {
        assertEquals(Shade.NOTHING, sendErrorNotification(true, Tally(running = 2, saved = 1), false))
        assertEquals(Shade.NOTHING, sendErrorNotification(true, Tally(saved = 1, failed = 2), true))
    }

    @Test
    fun `left while saves run the bar shows`() {
        assertEquals(Shade.BAR, sendErrorNotification(false, Tally(running = 2, saved = 1), false))
    }

    @Test
    fun `ended with failures off screen one line`() {
        assertEquals(Shade.LINE, sendErrorNotification(false, Tally(saved = 1, failed = 2), true))
    }

    @Test
    fun `ended clean posts nothing`() {
        assertEquals(Shade.NOTHING, sendErrorNotification(false, Tally(saved = 3), true))
    }
}
