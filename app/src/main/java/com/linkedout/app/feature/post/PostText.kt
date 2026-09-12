package com.linkedout.app.feature.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * Turns post text into tappable text: @mentions open the profile in the app,
 * and link tokens open the full URL.
 *
 * Nitter shortens links in the visible text ("example.com/some/pa…") while the
 * real URLs arrive separately in [links]. A token is linked only when it
 * matches one of those real URLs, or when it is a complete http(s) URL itself.
 * That is what keeps "Node.js" or "e.g." from becoming broken links.
 */
internal fun linkify(
    text: String,
    links: List<String>,
    linkColor: Color,
    onMention: (String) -> Unit
): AnnotatedString {
    val style = TextLinkStyles(style = SpanStyle(color = linkColor))
    val matches = TOKEN.findAll(text).toList()

    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            val token = match.value
            val mention = match.groups[1]?.value
            val target = if (mention == null) resolve(token, links) else null
            if (mention == null && target == null) continue

            append(text.substring(cursor, match.range.first))
            if (mention != null) {
                withLink(LinkAnnotation.Clickable("mention:$mention", style) { onMention(mention) }) {
                    append(token)
                }
            } else {
                withLink(LinkAnnotation.Url(target!!, style)) { append(token) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

private fun resolve(token: String, links: List<String>): String? {
    val wanted = normal(token)
    if (wanted.length < 4) return null
    links.firstOrNull { normal(it).startsWith(wanted) }?.let { return it }
    return token.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

private fun normal(value: String): String =
    value.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .trimEnd('…', '.', ',', ')', '!', '?')

/** Group 1 is a mention handle. Anything else is a link candidate. */
private val TOKEN = Regex(
    "(?<![\\w@])@([A-Za-z0-9_]{1,15})\\b" +
        "|(?:https?://)?(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?:/[^\\s]*)?"
)
