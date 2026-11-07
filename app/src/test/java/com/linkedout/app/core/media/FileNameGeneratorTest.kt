package com.linkedout.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * AntennaPod's cases, with a temporary folder where theirs used the Robolectric
 * context's external cache, as MTGA's test does:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/core/media/FileNameGeneratorTest.kt#FileNameGeneratorTest
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/test/java/de/danoeh/antennapod/net/download/serviceinterface/FilenameGeneratorTest.java
 */
class FileNameGeneratorTest {

    @get:Rule
    val cacheFolder = TemporaryFolder()

    @Test
    fun testGenerateFileName() {
        val result = FileNameGenerator.generateFileName("abc abc")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testGenerateFileName1() {
        val result = FileNameGenerator.generateFileName("ab/c: <abc")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testGenerateFileName2() {
        val result = FileNameGenerator.generateFileName("abc abc ")
        assertEquals(result, "abc abc")
        createFiles(result)
    }

    @Test
    fun testFeedTitleContainsApostrophe() {
        val result = FileNameGenerator.generateFileName("Feed's Title ...")
        assertEquals("Feeds Title", result)
    }

    @Test
    fun testFeedTitleContainsDash() {
        val result = FileNameGenerator.generateFileName("Left - Right")
        assertEquals("Left - Right", result)
    }

    @Test
    fun testFeedTitleContainsAccents() {
        val result = FileNameGenerator.generateFileName("Äàáâãå")
        assertEquals("Aaaaaa", result)
    }

    @Test
    fun testInvalidInput() {
        val result = FileNameGenerator.generateFileName("???")
        assertFalse(result.isEmpty())
    }

    @Test
    fun testLongFilename() {
        val longName = "x".repeat(20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result = FileNameGenerator.generateFileName(longName)
        assertTrue(result.length <= FileNameGenerator.MAX_FILENAME_LENGTH)
        createFiles(result)
    }

    @Test
    fun testLongFilenameNotEquals() {
        // Verify that the name is not just trimmed and different suffixes end up with the same name
        val longName = "x".repeat(20 + FileNameGenerator.MAX_FILENAME_LENGTH)
        val result1 = FileNameGenerator.generateFileName(longName + "a")
        val result2 = FileNameGenerator.generateFileName(longName + "b")
        assertNotEquals(result1, result2)
    }

    /** Tests if files can be created. */
    private fun createFiles(name: String) {
        val testFile = File(cacheFolder.root, name)
        assertTrue(testFile.mkdir())
        assertTrue(testFile.exists())
        assertTrue(testFile.delete())
        assertTrue(testFile.createNewFile())
    }
}
