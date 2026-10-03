package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeDocumentTest {

    @Test
    fun `reads a shared document out of the viewer a card embeds`() {
        // The shape of a profile card's viewer, October 2026, reduced.
        val card = """<iframe data-id="feed-paginated-document-content" data-native-document-config="{&quot;action&quot;:&quot;init&quot;,""" +
            """&quot;doc&quot;:{&quot;coverPages&quot;:[{&quot;type&quot;:&quot;image&quot;,&quot;config&quot;:{&quot;src&quot;:&quot;https://media.licdn.com/cover/0?e=1&amp;t=x&quot;}}],""" +
            """&quot;title&quot;:&quot;Lions Club&quot;,&quot;totalPageCount&quot;:10,""" +
            """&quot;manifestUrl&quot;:&quot;https://media.licdn.com/manifest/0?e=1&amp;t=y&quot;}}" title="Accessible PDF document"></iframe>"""
        val document = NativeDocument.read(card)!!
        assertEquals("Lions Club", document.title)
        assertEquals(10, document.pageCount)
        assertEquals("https://media.licdn.com/cover/0?e=1&t=x", document.coverUrl)
        assertEquals("https://media.licdn.com/manifest/0?e=1&t=y", document.manifestUrl)
    }

    @Test
    fun `a card without a document has none`() {
        assertNull(NativeDocument.read("<div>Just text</div>"))
    }
}
