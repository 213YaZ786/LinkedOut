package com.linkedout.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Same shape as PostIdTest, the sample being a company with accents in its address:
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/core/model/PostIdTest.kt#PostIdTest
class FollowedAccountTest {

    private val afib = "association-françaises-des-ingénieurs-biomédicaux---afib"

    @Test
    fun `keeps a name with accents, a company address or a plain name`() {
        assertEquals(afib, FollowedAccount.normalise(afib))
        assertEquals(afib, FollowedAccount.normalise("https://www.linkedin.com/company/$afib/"))
        assertEquals("john-doe-12ab", FollowedAccount.normalise("john-doe-12ab"))
    }

    @Test
    fun `refuses two words and a name over 120 characters, reduces a path to its last part`() {
        assertNull(FollowedAccount.normalise("two words"))
        assertNull(FollowedAccount.normalise("a".repeat(121)))
        assertEquals("a".repeat(120), FollowedAccount.normalise("a".repeat(120)))
        assertEquals("b", FollowedAccount.normalise("a/b"))
    }
}
