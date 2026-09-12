package com.linkedout.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.linkedout.app.core.network.ConnectivityMonitor
import com.linkedout.app.data.settings.SettingsStore
import org.koin.compose.koinInject

/**
 * How media behaves right now, from the reader's settings and the network.
 * One place, so the post list and the viewer never disagree.
 */
data class MediaPolicy(
    /** Pictures and videos wait for a tap: Wi-Fi only is on and the network is metered. */
    val hold: Boolean,
    val autoplay: Boolean,
    val startMuted: Boolean
)

@Composable
fun rememberMediaPolicy(): MediaPolicy {
    val store: SettingsStore = koinInject()
    val connectivity: ConnectivityMonitor = koinInject()
    val settings by store.settings.collectAsState()
    val metered by connectivity.metered.collectAsState()
    return MediaPolicy(
        hold = settings.mediaOnWifiOnly && metered,
        autoplay = settings.autoplayVideos,
        startMuted = settings.startMuted
    )
}
