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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<FollowedAccount>> = _accounts.asStateFlow()

    private fun load(): List<FollowedAccount> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<FollowedAccount>>(file.readText())
        }.getOrDefault(emptyList())
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

    private fun persist(updated: List<FollowedAccount>) {
        _accounts.value = updated
        runCatching { file.writeText(json.encodeToString(updated)) }
    }
}
