package com.linkedout.app.core.link

/**
 * What goes into the search field when the reader taps paste. Pure, no Android,
 * so it can be run outside a build.
 *
 * The clipboard rarely holds a bare address. A browser's address bar does, but
 * LinkedIn's own share sheet copies a sentence with the link inside it, and a
 * long press on a link in a page can bring back the surrounding line. So the
 * address is pulled out of whatever came, rather than assumed to be all of it.
 *
 * Nothing is rejected here. A text with no address in it is handed on as it
 * stands, so the field can say what is wrong with it in its own words.
 */
object PastedText {

    /** Leading wrappers a copied line drags along. */
    private const val OPENERS = "(<[{\"'«‹"

    /** Trailing wrappers and sentence punctuation. */
    private const val CLOSERS = ")>]},.;:!?\"'»›"

    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun query(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""

        URL.find(text)?.value?.let { return trimWrappers(it) }

        // No scheme. The address bar on some browsers hides it, and people
        // type linkedin.com/in/name without it too.
        text.split(' ', '\t', '\n', '\r')
            .firstOrNull { namesLinkedIn(trimWrappers(it)) }
            ?.let { return trimWrappers(it) }

        // Not an address at all. Keep the first line with something on it: a
        // vanity name pasted alone is a valid query, a paragraph is not, and
        // the field explains that better than a silent refusal would.
        return text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun namesLinkedIn(token: String): Boolean {
        val lower = token.lowercase()
        return lower.contains("linkedin.com/") || lower.contains("lnkd.in/")
    }

    private fun trimWrappers(token: String): String =
        token.trimStart(*OPENERS.toCharArray()).trimEnd(*CLOSERS.toCharArray())
}
