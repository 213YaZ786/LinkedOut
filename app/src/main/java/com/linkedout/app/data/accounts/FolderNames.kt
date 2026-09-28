package com.linkedout.app.data.accounts

import com.linkedout.app.core.model.FollowedAccount

/**
 * The rules for folder names, pure so they can be tested.
 *
 * One rule matters above the others: two names that differ only in case are
 * one folder, and every account in it carries the exact spelling the list
 * shows. 0.6.51 deduplicated the list ignoring case but filed the
 * account under whatever was typed, so filing into "news" beside an existing
 * "News" left the account in a folder Home could not find, and the account
 * vanished from every folder but the full stream. [resolve] is what prevents
 * that here: whatever the reader types becomes the spelling already in use.
 */
internal object FolderNames {

    /**
     * Main first, then the rest by name, with no blanks and no two names that
     * differ only in case. The first spelling of a name wins.
     */
    fun ordered(names: List<String>): List<String> {
        val kept = LinkedHashMap<String, String>()
        names.map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(FollowedAccount.MAIN, ignoreCase = true) }
            .forEach { kept.putIfAbsent(it.lowercase(), it) }
        return listOf(FollowedAccount.MAIN) + kept.values.sortedBy { it.lowercase() }
    }

    /**
     * The folder [typed] means among [existing]: the existing spelling when
     * the name is taken, Main for a blank or for any casing of Main, the
     * trimmed name otherwise.
     */
    fun resolve(typed: String, existing: List<String>): String {
        val clean = typed.trim()
        if (clean.isEmpty() || clean.equals(FollowedAccount.MAIN, ignoreCase = true)) return FollowedAccount.MAIN
        return existing.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
    }
}
