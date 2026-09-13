package com.linkedout.app.data.accounts

import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.FollowedAccount

/**
 * The rules of the accounts file, with no JSON library in sight.
 *
 * [SubscriptionCodec] keeps the parsing and the writing, which need
 * kotlinx.serialization and therefore cannot run outside a build. Everything
 * that decides what a line means lives here instead, so it can be exercised
 * against real files in a harness. That split exists because the one thing
 * this file gets wrong is never the JSON, it is which key to believe and what
 * a token points at.
 */
object SubscriptionFormat {

    /** One account read out of a file, with what it points at. */
    data class Entry(val handle: String, val kind: AccountKind = AccountKind.PERSON)

    /**
     * What a single record in the JSON list means, given the fields that may
     * carry it. [kindField] is LinkedOut's own `linkedout_kind`, [url] the
     * address a file from elsewhere might have, and neither is required.
     */
    fun entryOf(handle: String?, kindField: String? = null, url: String? = null): Entry? {
        val name = handle?.trim().orEmpty()
        if (name.isEmpty()) return null
        val kind = kindField?.takeIf { it.isNotBlank() }?.let(AccountKind::ofSegment)
            ?: url?.let(::kindOfLink)
            ?: AccountKind.PERSON
        return Entry(name, kind)
    }

    /**
     * Handles, @handles and addresses, one per line or separated by commas.
     *
     * An organisation address used to be dropped here, because the shared file
     * format has one column and no room for the kind, and a slug followed as a
     * person fails on every refresh. A plain address says the kind in its own
     * path, so there is nothing left to drop.
     */
    fun fromText(text: String): List<Entry> =
        text.split('\n', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                when (val link = LinkedInLink.parse(token)) {
                    is LinkedInLink.Profile -> Entry(link.handle)
                    is LinkedInLink.Company -> Entry(link.slug, AccountKind.ofSegment(link.segment))
                    // A post link names its author only sometimes, and the
                    // short /feed/update/ form never does.
                    is LinkedInLink.Post -> link.handle?.let { Entry(it) }
                    null -> if (token.contains("://")) null else Entry(token)
                }
            }

    /** Valid names only, deduplicated ignoring case, in file order. */
    fun clean(entries: List<Entry>): List<Entry> =
        entries.mapNotNull { entry ->
            FollowedAccount.normalise(entry.handle)?.let { entry.copy(handle = it) }
        }.distinctBy { it.handle.lowercase() }

    /** The address an account is read at, which is also what export writes. */
    fun addressOf(handle: String, kind: AccountKind): String =
        LinkedInLink.canonical("${kind.segment}/$handle")

    private fun kindOfLink(value: String): AccountKind? =
        (LinkedInLink.parse(value) as? LinkedInLink.Company)?.let { AccountKind.ofSegment(it.segment) }
}
