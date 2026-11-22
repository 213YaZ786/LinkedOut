package com.linkedout.app.core.system

import android.content.Context

/**
 * Runs a long load the app started by itself ("Looking up older posts" while
 * the archives answer, for instance) and posts nothing: the shade is for what
 * happens out of sight, and these loads run while the app is open.
 *
 * Ported from https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/background/RssSyncJob.kt#RssSyncJob,
 * whose doWork syncs the feeds and posts no notification for its sync (its only
 * notification, `notify`, is for new articles).
 * not ported: LOG_TAG (the callers log their own), runOnceRssSync and
 * schedulePeriodicRssSync and getMyPendingJob (job scheduling: nothing runs in
 * the background here).
 *
 * The class and [during] stay as they were so the readers that share this kit
 * compile unchanged.
 */
@Suppress("UNUSED_PARAMETER", "unused")
class LoadingNotice(private val context: Context) {

    suspend fun <T> during(text: String, work: suspend () -> T): T = work()
}
