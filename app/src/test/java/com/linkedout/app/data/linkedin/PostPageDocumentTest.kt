package com.linkedout.app.data.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A logged out post page with a shared PDF, September 2026, reduced to the
 * markers the parser reads. The config attribute keeps LinkedIn's shape: the
 * document sits under "doc", entity encoded, next to accessibility labels.
 */
class PostPageDocumentTest {

    @Test
    fun `reads the shared document`() {
        val post = PostPageParser().parse(page(DOCUMENT_IFRAME), "7508102288889098240")!!.main!!
        val document = post.document!!

        assertEquals("Example CV", document.title)
        assertEquals(1, document.pageCount)
        assertEquals("https://media.licdn.com/dms/image/v2/X/feedshare-document-cover-images_480/Y/0/1?e=2147483647&v=beta&t=c", document.coverUrl)
        assertEquals("https://media.licdn.com/dms/document/pl/v2/X/feedshare-document-master-manifest/Z/0/1?e=2147483647&v=beta&t=m", document.manifestUrl)
    }

    @Test
    fun `does not show the cover a second time as a picture`() {
        val post = PostPageParser().parse(page(DOCUMENT_IFRAME), "7508102288889098240")!!.main!!

        assertTrue(post.media.isEmpty())
    }

    @Test
    fun `a post without a document has none`() {
        val post = PostPageParser().parse(page(""), "7508102288889098240")!!.main!!

        assertNull(post.document)
    }

    private companion object {
        private const val Q = "&quot;"

        val DOCUMENT_IFRAME = "<iframe data-id=\"feed-paginated-document-content\" " +
            "data-native-document-config=\"{${Q}action${Q}:${Q}init${Q},${Q}a11y${Q}:{${Q}topbar${Q}:{${Q}downloadButton${Q}:${Q}Télécharger${Q}}}," +
            "${Q}doc${Q}:{${Q}coverPages${Q}:[{${Q}type${Q}:${Q}image${Q},${Q}config${Q}:{${Q}src${Q}:" +
            "${Q}https://media.licdn.com/dms/image/v2/X/feedshare-document-cover-images_480/Y/0/1?e=2147483647&amp;v=beta&amp;t=c${Q}}}]," +
            "${Q}subtitle${Q}:${Q}1 page${Q},${Q}title${Q}:${Q}Example CV ${Q},${Q}type${Q}:${Q}presentation${Q},${Q}totalPageCount${Q}:1," +
            "${Q}manifestUrl${Q}:${Q}https://media.licdn.com/dms/document/pl/v2/X/feedshare-document-master-manifest/Z/0/1?e=2147483647&amp;v=beta&amp;t=m${Q}}," +
            "${Q}i18n${Q}:{}}\" title=\"Document PDF accessible\"></iframe>"

        fun page(attachment: String) = """
            <html><head><link rel="canonical" href="https://fr.linkedin.com/posts/example_cv-activity-7508102288889098240-grq-"></head><body>
            <article class="main-feed-activity-card" data-activity-urn="urn:li:activity:7508102288889098240">
            <a href="https://fr.linkedin.com/in/example?trk=public_post_feed-actor-name">Example Person</a>
            <p class="attributed-text-segment-list__content" data-test-id="main-feed-activity-card__commentary">Looking for a first job.</p>
            $attachment
            </article></body></html>
        """.trimIndent()
    }
}
