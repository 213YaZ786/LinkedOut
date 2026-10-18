package com.linkedout.app.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the receiver acts on: only DownloadManager's own completion action
 * (https://developer.android.com/reference/android/app/DownloadManager#ACTION_DOWNLOAD_COMPLETE)
 * carrying a download id (EXTRA_DOWNLOAD_ID, absent when read back as -1),
 * the unit-testable part of a receiver, as Now in Android keeps its
 * Android-free logic apart (https://github.com/android/nowinandroid).
 */
class SaveOriginReceiverTest {

    @Test
    fun `a finished download with an id is acted on`() {
        assertTrue(SaveOriginReceiver.accepts("android.intent.action.DOWNLOAD_COMPLETE", 12))
        assertTrue(SaveOriginReceiver.accepts("android.intent.action.DOWNLOAD_COMPLETE", 0))
    }

    @Test
    fun `another action is ignored`() {
        assertFalse(SaveOriginReceiver.accepts("android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED", 12))
        assertFalse(SaveOriginReceiver.accepts("android.intent.action.BOOT_COMPLETED", 12))
        assertFalse(SaveOriginReceiver.accepts("android.intent.action.download_complete", 12))
        assertFalse(SaveOriginReceiver.accepts("", 12))
        assertFalse(SaveOriginReceiver.accepts(null, 12))
    }

    @Test
    fun `a missing id is ignored`() {
        assertFalse(SaveOriginReceiver.accepts("android.intent.action.DOWNLOAD_COMPLETE", -1))
        assertFalse(SaveOriginReceiver.accepts("android.intent.action.DOWNLOAD_COMPLETE", Long.MIN_VALUE))
    }
}
