package com.linkedout.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FollowedAccount(
    val handle: String,
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
