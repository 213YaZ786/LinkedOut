package com.linkedout.app.core.media

import com.linkedout.app.core.media.MediaSavingNotice.Progress
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
}
