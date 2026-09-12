package com.linkedout.app.core.link

/**
 * The address Share and Copy link hand out.
 *
 * In MTGA this took a server, because a post existed at several addresses and
 * the reader chose which one to pass on. LinkedIn has one address per post, so
 * there is no choice left to offer and nothing to configure. What remains is
 * picking the form that survives being pasted somewhere else.
 *
 * The long /posts/ form is preferred over /feed/update/. Both resolve, but the
 * long one carries the author and the first words of the post, so a pasted
 * link says what it points at before anyone opens it.
 */
object ShareLink {

    /**
     * [permalink] is what the page gave us and is used as it stands when it is
     * a LinkedIn address, with tracking parameters cut off.
     *
     * When there is no permalink the short /feed/update/ form is built instead,
     * and the author is deliberately not used to fake a long one: the long form
     * needs the post's own word slug, and a handmade /posts/<handle>_activity-
     * address does not parse back to this post. A link that cannot be reopened
     * is worse than a plain one.
     */
    @Suppress("UNUSED_PARAMETER")
    fun forPost(handle: String?, id: String, permalink: String?): String {
        val known = permalink?.takeIf { it.isNotBlank() && LinkedInLink.parse(it) != null }
        if (known != null) return known.substringBefore('?')
        return LinkedInLink.canonical("feed/update/urn:li:activity:$id")
    }

    fun forProfile(handle: String): String = LinkedInLink.profileUrl(handle)

    fun forCompany(slug: String): String = LinkedInLink.companyUrl(slug)
}
