package com.linkedout.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Which listed files are saved, in the shape of this app's still test:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/OfflineMediaStillTest.kt#OfflineMediaStillTest
 * The transfer states come from DownloadManager, see
 * https://github.com/rdoeffinger/Dictionary/blob/main/src/com/hughes/android/dictionary/DictionaryManagerActivity.kt#isDownloadActive
 */
class OfflineMediaUnfinishedTest {

    private val running = File("/media/someone/42.0.mp4")
    private val failed = File("/media/someone/42.1.mp4")
    private val finished = File("/media/someone/43.0.jpg")
    private val runningStill = File("/media/someone/42.0.still.jpg")
    private val failedStill = File("/media/someone/42.1.still.jpg")
    private val all = listOf(running, failed, finished, runningStill, failedStill)

    private val unfinished = setOf(running.absolutePath, runningStill.absolutePath)
    private val failedPaths = setOf(failed.absolutePath, failedStill.absolutePath)
    private val active = OfflineMedia.isDownloadActive(all, unfinished, failedPaths, cancel = false)
    private val cancelled = OfflineMedia.isDownloadActive(all, unfinished, failedPaths, cancel = true)
    private val indexed = all - active.toSet() - cancelled.toSet()

    @Test
    fun `a file whose transfer runs is not indexed and not deleted`() {
        assertEquals(listOf(running, runningStill), active)
        assertEquals(false, running in indexed)
        assertEquals(false, running in cancelled)
    }

    @Test
    fun `a failed transfer is to be deleted so a retry is not blocked`() {
        assertEquals(listOf(failed, failedStill), cancelled)
        assertEquals(false, failed in indexed)
    }

    @Test
    fun `a finished file is indexed`() {
        assertEquals(listOf(finished), indexed)
    }

    @Test
    fun `a still follows the same rule`() {
        assertEquals(false, runningStill in indexed)
        assertEquals(false, runningStill in cancelled)
        assertEquals(true, failedStill in cancelled)
    }

    @Test
    fun `with no transfer known every file is indexed`() {
        assertEquals(emptyList<File>(), OfflineMedia.isDownloadActive(all, emptySet(), emptySet(), cancel = false))
        assertEquals(emptyList<File>(), OfflineMedia.isDownloadActive(all, emptySet(), emptySet(), cancel = true))
    }
}
