package com.linkedout.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Files under JUnit's TemporaryFolder rule, as AtomicWriteTest does
 * (https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html),
 * and the cases of a small store: what is kept, the cap, the age, a restart,
 * a damaged file. The row and path rules follow the documented columns of
 * MediaStore.MediaColumns
 * (https://developer.android.com/reference/android/provider/MediaStore.MediaColumns).
 */
class SaveOriginTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private fun store(file: File = File(folder.root, "save-origin.json")) = SaveOriginStore(file) { now }

    @Test
    fun `a note is kept with its address`() {
        val store = store()
        store.add(7, "https://x.com/a/status/1")

        assertEquals(listOf(7L to "https://x.com/a/status/1"), store.pending())
    }

    @Test
    fun `the same id is noted once, the newest address kept`() {
        val store = store()
        store.add(7, "https://x.com/a/status/1")
        store.add(7, "https://x.com/a/status/2")

        assertEquals(listOf(7L to "https://x.com/a/status/2"), store.pending())
    }

    @Test
    fun `an empty store has nothing`() {
        assertTrue(store().pending().isEmpty())
    }

    @Test
    fun `removing forgets only those ids`() {
        val store = store()
        store.add(1, "https://x.com/a/status/1")
        store.add(2, "https://x.com/a/status/2")
        store.add(3, "https://x.com/a/status/3")

        store.remove(listOf(1L, 3L, 99L))

        assertEquals(listOf(2L), store.pending().map { it.first })
        store.remove(emptyList())
        assertEquals(listOf(2L), store.pending().map { it.first })
    }

    @Test
    fun `at most 500 are kept, the oldest go first`() {
        val store = store()
        for (id in 1L..(SaveOriginStore.MAX_ENTRIES + 20L)) {
            now++
            store.add(id, "https://x.com/a/status/$id")
        }

        val ids = store.pending().map { it.first }
        assertEquals(SaveOriginStore.MAX_ENTRIES, ids.size)
        assertEquals(21L, ids.first())
        assertEquals(SaveOriginStore.MAX_ENTRIES + 20L, ids.last())
    }

    @Test
    fun `a note older than seven days is dropped, a younger one stays`() {
        val store = store()
        store.add(1, "https://x.com/a/status/1")
        now += SaveOriginStore.MAX_AGE_MILLIS - 1
        store.add(2, "https://x.com/a/status/2")
        assertEquals(listOf(1L, 2L), store.pending().map { it.first })

        now += 2
        assertEquals(listOf(2L), store.pending().map { it.first })

        now += SaveOriginStore.MAX_AGE_MILLIS
        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `a note from the future is dropped, a clock set back must not keep it for ever`() {
        val store = store()
        store.add(1, "https://x.com/a/status/1")
        now -= 5_000

        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `a restart reads what was written`() {
        val file = File(folder.root, "save-origin.json")
        store(file).add(4, "https://x.com/a/status/4")

        assertEquals(listOf(4L to "https://x.com/a/status/4"), store(file).pending())
        assertFalse(File(folder.root, "save-origin.json.tmp").exists())
    }

    @Test
    fun `a damaged or empty file reads as nothing and is written over`() {
        val file = File(folder.root, "save-origin.json")
        for (text in listOf("", "{", "[1,2]", "null", "{\"entries\":\"x\"}")) {
            file.writeText(text)
            val store = store(file)
            assertTrue(text, store.pending().isEmpty())
            store.add(5, "https://x.com/a/status/5")
            assertEquals(text, listOf(5L), store.pending().map { it.first })
        }
    }

    @Test
    fun `a missing folder does not crash a write`() {
        val store = SaveOriginStore(File(folder.root, "none/save-origin.json")) { now }

        store.add(1, "https://x.com/a/status/1")

        assertTrue(store.pending().isEmpty())
    }

    @Test
    fun `a path inside the downloads folder is taken`() {
        assertEquals(
            "/storage/emulated/0/Download/MTGA/mtga_a_1.jpg",
            SaveOrigin.localPath("file:///storage/emulated/0/Download/MTGA/mtga_a_1.jpg", "/storage/emulated/0/Download")
        )
        assertEquals(
            "/storage/emulated/0/Download/Yi%E1%B8%8D/a.jpg".let { java.net.URI(it).path },
            SaveOrigin.localPath("file:///storage/emulated/0/Download/Yi%E1%B8%8D/a.jpg", "/storage/emulated/0/Download/")
        )
    }

    @Test
    fun `any other address names no file of ours`() {
        val downloads = "/storage/emulated/0/Download"
        listOf(
            null,
            "",
            "content://downloads/all_downloads/7",
            "https://example.org/Download/a.jpg",
            "file:///data/data/other/files/a.jpg",
            "file:///storage/emulated/0/DCIM/a.jpg",
            "file:///storage/emulated/0/Download",
            "file:///storage/emulated/0/Download/",
            "file:///storage/emulated/0/Download/../DCIM/a.jpg",
            "file:///storage/emulated/0/Download/./a.jpg",
            "file:///storage/emulated/0/Downloads/a.jpg",
            "file:///storage/emulated/0/Download/a b.jpg",
            "not a uri"
        ).forEach { assertNull(it, SaveOrigin.localPath(it, downloads)) }
    }

    @Test
    fun `the row to update is the file at that path owned by this app, nothing wider`() {
        val (where, args) = SaveOrigin.rowFilter("/storage/emulated/0/Download/MTGA/a.jpg", "com.mtga.app")

        assertEquals("_data = ? AND owner_package_name = ?", where)
        assertEquals(listOf("/storage/emulated/0/Download/MTGA/a.jpg", "com.mtga.app"), args.toList())
    }

    @Test
    fun `a path with filter characters stays an argument, never part of the clause`() {
        val (where, args) = SaveOrigin.rowFilter("/d/a' OR '1'='1", "com.mtga.app")

        assertFalse(where.contains("OR '1'"))
        assertEquals("/d/a' OR '1'='1", args[0])
    }
}
