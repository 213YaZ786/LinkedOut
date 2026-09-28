package com.linkedout.app.data.accounts

import com.linkedout.app.core.common.writeTextAtomically
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
        return FolderNames.ordered(stored + _accounts.value.map { it.folder })
    }

    /**
     * Returns false when the name is invalid or already followed.
     *
     * Two different pages can share a name, one person and one organisation,
     * so a name already followed as one kind is not followed again as the
     * other. That is a deliberate limit rather than an oversight: the rest of
     * the app keys caches and routes on the name alone.
     */
    @Synchronized
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
    @Synchronized
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

    @Synchronized
    fun remove(handle: String) =
        persist(_accounts.value.filterNot { it.handle.equals(handle, ignoreCase = true) })

    @Synchronized
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
    @Synchronized
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
     * Creates a folder and returns its name. A name already taken, whatever
     * its case, is not created twice: the existing one is returned so the
     * caller opens the folder the reader meant. Null for a blank name.
     */
    @Synchronized
    fun createFolder(name: String): String? {
        if (name.isBlank()) return null
        val resolved = FolderNames.resolve(name, _folders.value)
        if (_folders.value.none { it == resolved }) persistFolders(_folders.value + resolved)
        return resolved
    }

    /**
     * Files an account. A blank name means the main folder.
     *
     * The name goes through [FolderNames.resolve]. Before 0.6.52 filing into
     * "news" beside an existing "News" kept one folder in the list but filed
     * the account under "news", which Home could not match, so the account
     * vanished from every folder but the full stream.
     */
    @Synchronized
    fun setFolder(handle: String, folder: String) {
        val resolved = FolderNames.resolve(folder, _folders.value)
        val current = _accounts.value.firstOrNull { it.handle.equals(handle, ignoreCase = true) } ?: return
        if (_folders.value.none { it == resolved }) persistFolders(_folders.value + resolved)
        if (current.folder == resolved) return
        persist(
            _accounts.value.map {
                if (it.handle.equals(handle, ignoreCase = true)) it.copy(folder = resolved) else it
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
    @Synchronized
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
     * Renames a folder, in the list and on every account that carries it, and
     * returns the name it now has, or null when nothing changed. Renaming
     * onto a name already in use, whatever its case, merges the two under
     * that name's spelling, since two folders with one name would be
     * indistinguishable in Home. Main is neither renamed nor a target.
     */
    @Synchronized
    fun renameFolder(from: String, to: String): String? {
        if (from == FollowedAccount.MAIN || to.isBlank() || _folders.value.none { it == from }) return null
        val others = _folders.value.filterNot { it == from }
        val target = FolderNames.resolve(to, others)
        if (target == FollowedAccount.MAIN || target == from) return null
        persistFolders(others + target)
        if (_accounts.value.any { it.folder == from }) {
            persist(_accounts.value.map { if (it.folder == from) it.copy(folder = target) else it })
        }
        return target
    }

    private fun persist(updated: List<FollowedAccount>) {
        _accounts.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }

    private fun persistFolders(names: List<String>) {
        val list = FolderNames.ordered(names)
        _folders.value = list
        // Main is always first and always implicit, so it is dropped rather
        // than stored. A file that somehow held it would load the same way.
        runCatching { foldersFile.writeTextAtomically(json.encodeToString(list.drop(1))) }
    }
}
