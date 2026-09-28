package com.linkedout.app.data.accounts

import com.linkedout.app.core.model.FollowedAccount.Companion.MAIN
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNamesTest {

    @Test
    fun `main comes first, the rest by name, case duplicates dropped`() {
        assertEquals(
            listOf(MAIN, "Friends", "News"),
            FolderNames.ordered(listOf("News", " Friends ", "news", "main", "", "  "))
        )
    }

    @Test
    fun `a name that differs only in case resolves to the spelling in use`() {
        assertEquals("News", FolderNames.resolve("news", listOf(MAIN, "News")))
        assertEquals("News", FolderNames.resolve("  NEWS ", listOf(MAIN, "News")))
    }

    @Test
    fun `blank and any casing of main mean main`() {
        assertEquals(MAIN, FolderNames.resolve("", listOf(MAIN)))
        assertEquals(MAIN, FolderNames.resolve("MAIN", listOf(MAIN, "News")))
    }

    @Test
    fun `a new name is kept as typed, trimmed`() {
        assertEquals("Sport", FolderNames.resolve(" Sport ", listOf(MAIN, "News")))
    }
}
