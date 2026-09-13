package com.linkedout.app.core.model

import kotlinx.serialization.Serializable

/**
 * What kind of page a followed name points at, and therefore which address to
 * ask for and which parser reads the answer. [segment] is the part of the path
 * before the name, which is the whole difference between the four.
 *
 * A slug alone cannot say: linkedin.com/in/chu-nantes and
 * linkedin.com/company/chu-nantes are both well formed and only one exists.
 * That is exactly the mistake that produced a 999 in the request log, so the
 * kind is remembered rather than guessed.
 */
@Serializable
enum class AccountKind(val segment: String) {
    PERSON("in"),
    COMPANY("company"),
    SCHOOL("school"),
    SHOWCASE("showcase");

    val isOrganisation: Boolean get() = this != PERSON

    companion object {
        fun ofSegment(segment: String): AccountKind =
            entries.firstOrNull { it.segment == segment.lowercase() } ?: PERSON
    }
}

/**
 * [kind] defaults to PERSON so an accounts file written before organisations
 * existed still loads, with every name in it read as a person, which is what
 * it was.
 */
@Serializable
data class FollowedAccount(
    val handle: String,
    val kind: AccountKind = AccountKind.PERSON,
    val displayName: String? = null,
    val addedAtMillis: Long = 0L
) {
    companion object {
        /**
         * A LinkedIn vanity name: the part after /in/. Letters, digits and
         * hyphens, and long, because LinkedIn appends a hash to duplicates.
         * Validated locally so a typo fails instantly instead of costing a
         * request to a host that rate limits guests hard.
         */
        private val VALID = Regex("^[A-Za-z0-9\\-_%.]{1,120}$")

        fun normalise(raw: String): String? {
            val cleaned = raw.trim()
                .removePrefix("@")
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
            return if (VALID.matches(cleaned)) cleaned else null
        }
    }
}
