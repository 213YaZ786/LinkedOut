package com.linkedout.app.feature.settings

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.accounts.SubscriptionCodec
import com.linkedout.app.core.web.BrowserData
import com.linkedout.app.core.web.GuestCookies
import com.linkedout.app.core.web.WebSession
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.settings.Settings
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.data.settings.StartTab
import com.linkedout.app.data.settings.ThemeMode
import com.linkedout.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val store: SettingsStore,
    private val cache: FeedCache,
    private val accounts: AccountStore,
    private val cookies: GuestCookies,
    private val session: WebSession,
    private val context: Context
) : ViewModel() {

    /** One line to show after an import or export, then cleared. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    fun setKeepPostsDays(days: Int) {
        store.update { it.copy(keepPostsDays = days) }
        viewModelScope.launch {
            cache.applyRetention()
            measureStorage()
        }
    }

    /** Writes the followed list to a file the reader picked. */
    fun exportAccounts(uri: Uri) {
        val list = accounts.accounts.value
        viewModelScope.launch {
            val text = SubscriptionCodec.export(list)
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }
            _message.value = if (written) {
                "Exported ${list.size} accounts"
            } else {
                "Could not write that file"
            }
        }
    }

    /**
     * Follows every account found in a file the reader picked. Accepts
     * Fritter, Squawker and LinkedOut exports, or plain text with handles.
     */
    fun importAccounts(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        // An account list is a few kilobytes. A huge file is
                        // not one, and reading it whole would only cost memory.
                        // readNBytes would be shorter, but needs Android 13.
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (out.size() < MAX_IMPORT_BYTES) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        String(out.toByteArray())
                    }
                }.getOrNull()
            }
            if (text == null) {
                _message.value = "Could not read that file"
                return@launch
            }
            val entries = SubscriptionCodec.import(text)
            _message.value = if (entries.isEmpty()) {
                "No accounts found in that file"
            } else {
                val added = accounts.addAll(entries)
                val already = entries.size - added
                "Followed $added new accounts" + if (already > 0) ", $already already followed" else ""
            }
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }

    val settings: StateFlow<Settings> = store.settings

    private val _storageBytes = MutableStateFlow<Long?>(null)
    val storageBytes: StateFlow<Long?> = _storageBytes.asStateFlow()

    /**
     * How many guest cookies LinkedIn has set on this phone. Shown as a
     * number rather than a switch, because the reader should be able to see
     * that something is held before deciding to erase it.
     */
    private val _guestCookies = MutableStateFlow(0)
    val guestCookies: StateFlow<Int> = _guestCookies.asStateFlow()

    init {
        measureStorage()
    }

    fun setTheme(mode: ThemeMode) = store.update { it.copy(themeMode = mode) }

    fun setPureBlack(enabled: Boolean) = store.update { it.copy(pureBlack = enabled) }

    fun setShowCounts(enabled: Boolean) = store.update { it.copy(showCounts = enabled) }

    fun setTextScale(scale: Float) = store.update { it.copy(textScale = scale) }

    fun setCompactPosts(enabled: Boolean) = store.update { it.copy(compactPosts = enabled) }

    fun setSquareAvatars(enabled: Boolean) = store.update { it.copy(squareAvatars = enabled) }

    fun setMediaOnWifiOnly(enabled: Boolean) = store.update { it.copy(mediaOnWifiOnly = enabled) }

    fun setAutoplayVideos(enabled: Boolean) = store.update { it.copy(autoplayVideos = enabled) }

    fun setStartMuted(enabled: Boolean) = store.update { it.copy(startMuted = enabled) }

    fun setStartTab(tab: StartTab) = store.update { it.copy(startTab = tab) }

    fun setBackgroundSync(enabled: Boolean) {
        store.update { it.copy(backgroundSync = enabled) }
        applySchedule()
    }

    /**
     * Turning notifications on starts the clock: only posts published after
     * this moment are ever announced. The permission is asked by the screen
     * before this is called.
     */
    fun setNotifyNewPosts(enabled: Boolean) = store.update {
        it.copy(
            notifyNewPosts = enabled,
            notifySinceMillis = if (enabled) System.currentTimeMillis() else it.notifySinceMillis
        )
    }

    fun setInterval(minutes: Int) {
        store.update { it.copy(syncIntervalMinutes = minutes) }
        applySchedule()
    }

    fun setWifiOnly(enabled: Boolean) {
        store.update { it.copy(syncOnWifiOnly = enabled) }
        applySchedule()
    }

    /** Deletes saved posts only. Followed accounts and settings stay. */
    fun clearSavedPosts() {
        viewModelScope.launch {
            cache.clear()
            measureStorage()
        }
    }

    /**
     * Forgets the guest session, and the browser engine's one with it.
     *
     * Two stores, because there are two ways this app reads. The jar in
     * [GuestCookies] is the one the number above counts. The engine that gets
     * past the sign in wall keeps its own, which the app never reads and
     * cannot show, and that is precisely why this button has to erase it too.
     * [WebSession.forget] drops the judgements made about the host, so nothing
     * keeps reading with a jar that is now empty.
     *
     * The next read starts as a first time visitor, which costs one request
     * and may bring back a refusal that cookies were getting past.
     */
    fun clearBrowsingData() {
        cookies.clear()
        BrowserData.clear()
        session.forget()
        _guestCookies.value = 0
        _message.value = "Browsing data cleared"
    }

    fun measureStorage() {
        viewModelScope.launch { _storageBytes.value = cache.sizeBytes() }
        _guestCookies.value = cookies.count()
    }

    private fun applySchedule() {
        val current = store.current
        if (current.backgroundSync) {
            SyncWorker.schedule(context, current.syncIntervalMinutes, current.syncOnWifiOnly)
        } else {
            SyncWorker.cancel(context)
        }
    }
}
