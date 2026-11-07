package com.linkedout.app.feature.accounts

import com.linkedout.app.data.read.ReadState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The unread count of an account, as Feeder's FeedItemDao.getFeedItemCount counts the items of a feed with no read
 * time (https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/db/room/FeedItemDao.kt#getFeedItemCount).
 */
class UnreadCountTest {

    private val feedItems = listOf("a", "b", "c", "d")

    @Test
    fun `only the posts not in the read marks count`() {
        assertEquals(2, getFeedItemCount(feedItems, ReadState(ready = true, ids = setOf("a", "c"))))
    }

    @Test
    fun `every post read counts zero, and an account with no posts too`() {
        assertEquals(0, getFeedItemCount(feedItems, ReadState(ready = true, ids = feedItems.toSet())))
        assertEquals(0, getFeedItemCount(emptyList(), ReadState(ready = true)))
    }

    @Test
    fun `nothing counts until the read marks are loaded`() {
        assertEquals(0, getFeedItemCount(feedItems, ReadState(ready = false)))
    }
}
