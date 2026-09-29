package com.linkedout.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun `a later version is newer, number by number, suffixes aside`() {
        assertTrue(Updates.isNewer("2.10.0", "2.9.22"))
        assertTrue(Updates.isNewer("0.3.10", "0.3.9"))
        assertTrue(Updates.isNewer("1.0", "0.9.9"))
        assertFalse(Updates.isNewer("2.9.22", "2.10.0"))
        assertFalse(Updates.isNewer("0.3.4", "0.3.4"))
        assertFalse(Updates.isNewer("0.3.4", "0.3.4-debug"))
        assertTrue(Updates.isNewer("0.3.5", "0.3.4-debug"))
    }
}
