package com.linkedout.app.data.linkedin

/**
 * The small string tools both LinkedIn parsers need.
 *
 * Deliberately not a HTML library and not a JSON library. LinkedIn's guest
 * pages are around 200 kB of generated markup with hundreds of empty template
 * comments in them, and the few dozen values worth having are all reachable by
 * looking for a marker and reading forward. A DOM would parse all of it to
 * answer the same questions, on a phone, for every post in a feed.
 *
 * The JSON-LD side is scanned the same way rather than deserialised, which has
 * a second benefit: this file compiles and runs outside Android with no
 * dependency at all, so the parsers can be checked against real captured pages
 * before anything is built.
 *
 * Every extractor returns null rather than throwing. One markup change should
 * cost a field, not a page.
 */
internal object Markup {

    // ---- HTML --------------------------------------------------------------

    /** The value of [attribute] in the tag that starts at or after [from]. */
    fun String.attributeAfter(from: Int, attribute: String, window: Int = 4000): String? {
        if (from < 0) return null
        val at = indexOf("$attribute=\"", from)
        if (at < 0 || at - from > window) return null
        val start = at + attribute.length + 2
        val end = indexOf('"', start)
        return if (end < 0) null else substring(start, end).takeIf { it.isNotEmpty() }
    }

    fun String.attributeAfter(marker: String, attribute: String, window: Int = 4000): String? =
        attributeAfter(indexOf(marker), attribute, window)

    /**
     * The text of the element whose opening tag contains [marker], with tags
     * stripped and entities decoded. Reads to the first [closeTag] after the
     * marker, which is why it must not be used on an element that nests
     * another of the same kind.
     */
    fun String.textAfter(marker: String, closeTag: String = "</div>"): String? =
        textAfter(indexOf(marker), closeTag)

    fun String.textAfter(markerAt: Int, closeTag: String = "</div>"): String? {
        if (markerAt < 0) return null
        val open = indexOf('>', markerAt)
        if (open < 0) return null
        val close = indexOf(closeTag, open)
        if (close < 0) return null
        return plainText(substring(open + 1, close))
    }

    /** Tags out, entities decoded, runs of blank lines collapsed, trimmed. */
    fun plainText(html: String): String {
        val builder = StringBuilder(html.length)
        var inTag = false
        for (character in html) {
            when {
                character == '<' -> inTag = true
                character == '>' -> inTag = false
                !inTag -> builder.append(character)
            }
        }
        return decodeEntities(builder.toString())
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(BLANK_RUN, "\n\n")
            .trim()
    }

    fun decodeEntities(input: String): String {
        if ('&' !in input) return input
        var out = input
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&nbsp;", " ").replace("&middot;", "\u00b7")
            .replace("&ldquo;", "\u201c").replace("&rdquo;", "\u201d")
            .replace("&hellip;", "\u2026").replace("&mdash;", "\u2014")
            .replace("&ndash;", "\u2013").replace("&amp;", "&")
        if ("&#" in out) {
            out = NUMERIC_ENTITY.replace(out) { match ->
                val hex = match.groupValues[1].isNotEmpty()
                val code = match.groupValues[2].toIntOrNull(if (hex) 16 else 10)
                if (code != null && code in 1..0x10FFFF) String(Character.toChars(code))
                else match.value
            }
        }
        return out
    }

    /** Every http(s) link inside [html], in order, deduplicated. */
    fun links(html: String): List<String> =
        HREF.findAll(html).map { decodeEntities(it.groupValues[1]) }
            .filter { it.startsWith("http") }
            .map { it.substringBefore("?trk=") }
            .distinct()
            .toList()

    // ---- JSON-LD -----------------------------------------------------------

    /** The contents of the page's JSON-LD blocks, largest first. */
    fun jsonLdBlocks(html: String): List<String> {
        val blocks = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = html.indexOf("application/ld+json", from)
            if (at < 0) break
            val open = html.indexOf('>', at)
            val close = if (open < 0) -1 else html.indexOf("</script>", open)
            if (open < 0 || close < 0) break
            blocks += html.substring(open + 1, close).trim()
            from = close
        }
        return blocks.sortedByDescending(String::length)
    }

    /**
     * The string value of [key] starting at [from]. Handles the escapes LinkedIn
     * actually emits in JSON-LD: \n, \", \\ and \uXXXX.
     */
    fun String.jsonString(key: String, from: Int = 0): String? {
        val at = indexOf("\"$key\":\"", from)
        if (at < 0) return null
        return jsonStringAt(at + key.length + 4)
    }

    fun String.jsonStringAt(start: Int): String? {
        val builder = StringBuilder()
        var index = start
        while (index < length) {
            when (val character = this[index]) {
                '"' -> return builder.toString()
                '\\' -> {
                    when (val escape = getOrNull(index + 1)) {
                        'n' -> builder.append('\n')
                        't' -> builder.append('\t')
                        'r' -> Unit
                        'u' -> {
                            val hex = substring(index + 2, minOf(index + 6, length))
                            hex.toIntOrNull(16)?.let { builder.append(it.toChar()) }
                            index += 4
                        }
                        null -> return builder.toString()
                        else -> builder.append(escape)
                    }
                    index++
                }
                else -> builder.append(character)
            }
            index++
        }
        return builder.toString()
    }

    /** The numeric value of [key] starting at [from]. */
    fun String.jsonNumber(key: String, from: Int = 0): Long? {
        val at = indexOf("\"$key\":", from)
        if (at < 0) return null
        var index = at + key.length + 3
        while (index < length && this[index] == ' ') index++
        val start = index
        while (index < length && (this[index].isDigit() || this[index] == '-')) index++
        return substring(start, index).toLongOrNull()
    }

    /**
     * The offsets where each JSON object of type [type] begins, so a caller can
     * read its fields without slicing the whole graph into objects. LinkedIn
     * writes "@type" and sometimes "@context" first, so matching on the type
     * marker is enough to find the record.
     */
    fun String.objectsOfType(type: String): List<Int> {
        val found = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = indexOf("\"@type\":\"$type\"", from)
                .takeIf { it >= 0 }
                ?: indexOf("\"@type\": \"$type\"", from)
            if (at < 0) break
            found += at
            from = at + type.length
        }
        return found
    }

    /**
     * The offset of [key] among the own keys of the object containing [at],
     * ignoring the keys of anything nested inside it, or -1.
     *
     * [jsonString] and [jsonNumber] scan forward and stop at the first match,
     * which on a real graph reads the wrong record more often than the right
     * one: a post's own "url" arrives after its author's, and a person's own
     * "description" after the ones on the schools they attended. Reading a
     * record's own fields has to respect nesting, so this walks braces and
     * brackets and only matches at depth one.
     */
    fun String.ownKey(key: String, at: Int): Int {
        val needle = "\"$key\":"
        var index = lastIndexOf('{', at).coerceAtLeast(0)
        var depth = 0
        var inString = false
        while (index < length) {
            val character = this[index]
            when {
                inString && character == '\\' -> index++
                character == '"' -> {
                    // A space after the colon is legal JSON and LinkedIn does
                    // not emit one today, but a pretty printed page would
                    // silently return nothing for every field.
                    if (!inString && depth == 1 &&
                        (startsWith(needle, index) || startsWith("$needle ", index))
                    ) {
                        return index
                    }
                    inString = !inString
                }
                inString -> Unit
                character == '{' || character == '[' -> depth++
                character == '}' || character == ']' -> {
                    depth--
                    if (depth == 0) return -1
                }
            }
            index++
        }
        return -1
    }

    /** [jsonString] restricted to the own keys of the object containing [at]. */
    fun String.ownString(key: String, at: Int): String? {
        val found = ownKey(key, at)
        if (found < 0) return null
        var quote = found + key.length + 3
        while (getOrNull(quote) == ' ') quote++
        return if (getOrNull(quote) != '"') null else jsonStringAt(quote + 1)
    }

    /** [jsonNumber] restricted to the own keys of the object containing [at]. */
    fun String.ownNumber(key: String, at: Int): Long? {
        val found = ownKey(key, at)
        return if (found < 0) null else jsonNumber(key, found)
    }

    /**
     * The end of the JSON object that contains [at], found by walking braces.
     * Needed because the fields of one post must not be read from the next.
     */
    fun String.objectEnd(at: Int): Int {
        var depth = 0
        var index = lastIndexOf('{', at).coerceAtLeast(0)
        var inString = false
        while (index < length) {
            val character = this[index]
            when {
                inString && character == '\\' -> index++
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return length
    }

    private val BLANK_RUN = Regex("\n{3,}")
    /**
     * Decimal and hexadecimal both. LinkedIn writes accents as "&#xF4;" on its
     * French pages, so a decimal only pattern left every accented word mangled
     * and nothing in an English page ever showed it.
     */
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)
    private val HREF = Regex("""href="([^"]+)"""")
}
