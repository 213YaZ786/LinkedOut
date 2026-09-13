package com.linkedout.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The tab the app opens on. LAST reopens whichever tab was shown last. */
@Serializable
enum class StartTab { HOME, ACCOUNTS, LAST }

@Serializable
data class Settings(
    /** Poll followed accounts in the background so history accumulates. */
    val backgroundSync: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val syncOnWifiOnly: Boolean = true,
    /** A notification when background checks find new posts. Needs backgroundSync. */
    val notifyNewPosts: Boolean = false,
    /**
     * When notifications were turned on. Nothing older is ever announced, so
     * switching them on does not flood the shade with the backlog.
     */
    val notifySinceMillis: Long = 0,
    /** Home filters. Kept across launches, because a filter is a reading habit. */
    val homeHideReplies: Boolean = false,
    val homeHideReposts: Boolean = false,
    val homeMediaOnly: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Reply, repost, like and view counts under posts. */
    val showCounts: Boolean = true,
    /** Multiplier on every text style, one of the steps in ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    val compactPosts: Boolean = false,
    val squareAvatars: Boolean = false,
    /** Videos open silent. GIFs are always silent, they have no sound. */
    val startMuted: Boolean = false,
    /** Videos start by themselves when opened. GIFs always loop. */
    val autoplayVideos: Boolean = true,
    /**
     * On a metered network, pictures and videos wait for a tap. Off by
     * default: it is a data saver the reader chooses to impose.
     */
    val mediaOnWifiOnly: Boolean = false,
    /** Saved posts older than this many days are dropped. 0 keeps everything. */
    val keepPostsDays: Int = 0,
    val startTab: StartTab = StartTab.HOME,
    /** Index of the tab shown last, for StartTab.LAST. Recorded on every switch. */
    val lastTab: Int = 0,
    /**
     * The first launch guide was closed. It is only offered when nothing is
     * followed yet, so an update never shows it to someone already set up.
     */
    val welcomeSeen: Boolean = false
)

/**
 * Small preference file, same plain JSON approach as the rest of LinkedOut's
 * storage. Nothing here is a secret, and none of it leaves the device.
 *
 * Fields removed from [Settings] do not break an existing file: the reader is
 * built with ignoreUnknownKeys, so a settings.json written by an older build
 * still loads and the dropped keys are simply forgotten on the next write.
 */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        runCatching { file.writeText(json.encodeToString(updated)) }
    }
}
