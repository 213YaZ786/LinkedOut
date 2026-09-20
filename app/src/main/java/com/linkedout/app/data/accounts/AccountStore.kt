package com.linkedout.app.data.accounts

import android.content.Context
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.FollowedAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The list of handles you follow. Local only, never transmitted anywhere.
 * Same plain JSON approach as the instance list, for the same reasons.
 */
class AccountStore(context: Context) {

    private val file = File(context.filesDir, "accounts.json")
    private val foldersFile = File(context.filesDir, "folders.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<FollowedAccount>> = _accounts.asStateFlow()

    /**
     * Every folder that exists, main first and the rest by name.
     *
     * A folder used to be nothing but the name its accounts carried, so an
     * empty one did not exist and could not be chosen in Home. The names are
     * kept here instead, which is what lets a folder be created first and
     * filled afterwards. Membership still lives on the account, so there is
     * exactly one place that says where an account is.
     *
     * Main is never written to the file. It is where an account lands when it
     * has been put nowhere, so it exists whether or not anything is in it.
     */
    private val _folders = MutableStateFlow(loadFolders())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private fun load(): List<FollowedAccount> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FollowedAccount>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun loadFolders(): List<String> {
        val stored = if (!foldersFile.exists()) {
            emptyList()
        } else {
            runCatching {
                json.decodeFromString<List<String>>(foldersFile.readText())
            }.getOrDefault(emptyList())
        }
        // The names the accounts carry are folded in, so an install that had
        // folders before this file existed keeps every one of them without the
        // reader doing anything. Nothing is written until something changes.
        return ordered(stored + _accounts.value.map { it.folder })
    }

    /**
     * Main first, then the rest by name, with no blanks and no two names that
     * differ only in case. The first spelling of a name wins, which is the one
     * the reader typed first.
     */
    private fun ordered(names: List<String>): List<String> {
        val kept = LinkedHashMap<String, String>()
        names.map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(FollowedAccount.MAIN, ignoreCase = true) }
            .forEach { kept.putIfAbsent(it.lowercase(), it) }
        return listOf(FollowedAccount.MAIN) + kept.values.sortedBy { it.lowercase() }
    }

    /**
     * Returns false when the name is invalid or already followed.
     *
     * Two different pages can share a name, one person and one organisation,
     * so a name already followed as one kind is not followed again as the
     * other. That is a deliberate limit rather than an oversight: the rest of
     * the app keys caches and routes on the name alone.
     */
    fun add(rawHandle: String, kind: AccountKind = AccountKind.PERSON): Boolean {
        val handle = FollowedAccount.normalise(rawHandle) ?: return false
        if (_accounts.value.any { it.handle.equals(handle, ignoreCase = true) }) return false
        persist(
            _accounts.value + FollowedAccount(
                handle = handle,
                kind = kind,
                addedAtMillis = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * Follows every valid handle not already followed, in one write. Returns
     * how many were new. Used by import, where fifty separate writes would
     * also mean fifty separate list updates for Home to react to.
     */
    fun addAll(entries: List<SubscriptionFormat.Entry>): Int {
        val known = _accounts.value.map { it.handle.lowercase() }.toMutableSet()
        val now = System.currentTimeMillis()
        val fresh = entries.mapNotNull { entry ->
            FollowedAccount.normalise(entry.handle)?.let { entry.copy(handle = it) }
        }
            .filter { known.add(it.handle.lowercase()) }
            .map { FollowedAccount(handle = it.handle, kind = it.kind, addedAtMillis = now) }
        if (fresh.isNotEmpty()) persist(_accounts.value + fresh)
        return fresh.size
    }

    fun remove(handle: String) =
        persist(_accounts.value.filterNot { it.handle.equals(handle, ignoreCase = true) })

    fun updateDisplayName(handle: String, displayName: String) {
        // Sources that cannot tell send a blank or the handle itself. Neither
        // should overwrite a real name learned earlier.
        if (displayName.isBlank() || displayName.equals(handle, ignoreCase = true)) return
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        if (current.displayName == displayName) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(displayName = displayName) else it
            }
        )
    }

    /**
     * Files an account under the right kind after a read proved it.
     *
     * A person and an organisation live at two different addresses and only
     * one of them answers. Several accounts in the logs were followed as
     * people while being companies, so every refresh spent a request on a 404
     * and the reader was told the account did not exist. The read that finds
     * the real page says so here, once, and the next refresh goes straight
     * to the right address.
     */
    fun updateKind(handle: String, kind: AccountKind) {
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        if (current.kind == kind) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(kind = kind) else it
            }
        )
    }

    /**
     * Creates a folder and returns its name. A name already taken is not
     * created twice: the existing one is returned, with its own spelling, so
     * the caller can open the folder the reader meant. Null means the name was
     * blank.
     */
    fun createFolder(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = _folders.value.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existing != null) return existing
        persistFolders(_folders.value + clean)
        return clean
    }

    /** Files an account. A blank name means the main folder. */
    fun setFolder(handle: String, folder: String) {
        val clean = folder.trim().takeIf { it.isNotEmpty() } ?: FollowedAccount.MAIN
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        // An account can be filed into a folder that is not in the list yet,
        // for instance from a subscription file written elsewhere. Filing it
        // makes the folder exist rather than losing it.
        if (!clean.equals(FollowedAccount.MAIN, ignoreCase = true) &&
            _folders.value.none { it == clean }
        ) {
            persistFolders(_folders.value + clean)
        }
        if (current.folder == clean) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(folder = clean) else it
            }
        )
    }

    /**
     * Deletes a folder. Its accounts go back to the main folder and nothing is
     * unfollowed. An empty folder is deleted too, which the derived version
     * could not do: it had nothing to act on.
     *
     * The main folder cannot be deleted, it is where everything lands.
     */
    fun deleteFolder(name: String) {
        if (name == FollowedAccount.MAIN) return
        if (_folders.value.none { it == name }) return
        persistFolders(_folders.value.filterNot { it == name })
        if (_accounts.value.any { it.folder == name }) {
            persist(
                _accounts.value.map {
                    if (it.folder == name) it.copy(folder = FollowedAccount.MAIN) else it
                }
            )
        }
    }

    /**
     * Renames a folder, in the list and on every account that carries it.
     * Renaming onto a name that already exists merges the two, since two
     * folders with one name would be indistinguishable in Home.
     */
    fun renameFolder(from: String, to: String) {
        val clean = to.trim()
        if (from == FollowedAccount.MAIN || clean.isEmpty() || clean == from) return
        // Renaming to "Main" would file accounts under a name that only looks
        // like the main folder. Sending them there is what delete is for.
        if (clean.equals(FollowedAccount.MAIN, ignoreCase = true)) return
        if (_folders.value.none { it == from }) return
        persistFolders(_folders.value.filterNot { it == from } + clean)
        if (_accounts.value.any { it.folder == from }) {
            persist(
                _accounts.value.map { if (it.folder == from) it.copy(folder = clean) else it }
            )
        }
    }

    private fun persist(updated: List<FollowedAccount>) {
        _accounts.value = updated
        runCatching { file.writeText(json.encodeToString(updated)) }
    }

    private fun persistFolders(names: List<String>) {
        val list = ordered(names)
        _folders.value = list
        // Main is always first and always implicit, so it is dropped rather
        // than stored. A file that somehow held it would load the same way.
        runCatching { foldersFile.writeText(json.encodeToString(list.drop(1))) }
    }
}
