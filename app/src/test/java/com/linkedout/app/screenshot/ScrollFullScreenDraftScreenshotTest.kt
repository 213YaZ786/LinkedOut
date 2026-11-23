package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.feature.BarAction
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.ZoneSurface
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.plus
import com.linkedout.app.ui.component.scrolledFromTop
import com.linkedout.app.ui.component.underFloatingTopBar
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 26), nothing of app/src/main changes: the content scrolls on the whole
 * screen with no fade at all (no underFloatingTopBar), and the top capsule folds away while scrolling down and comes
 * back on the first upward scroll: Material's enterAlwaysScrollBehavior (EnterAlwaysScrollBehavior, its
 * nestedScrollConnection and heightOffset), which FloatingTopBar already supports through its scrollBehavior:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#enterAlwaysScrollBehavior
 * The Connection screen is scrolled four rows down, the frame is [ConnectionDraftScreen]: scroll_full_screen_draft, the
 * capsule fully folded (rows up to the window's top, under the status bar); scroll_full_screen_draft_back, the capsule
 * coming back (half the offset, a scroll up). Ported from MTGA's draft:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ScrollFullScreenDraftScreenshotTest.kt#ScrollFullScreenDraftScreenshotTest
 * with its frame, DiagnosticsDraftScreen:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ConnectionCheckRingDraftScreenshotTest.kt#DiagnosticsDraftScreen
 * redrawn from LinkedOut's own screen, a faithful copy with fixed data (ConnectionScreen is bound to its HostThrottle):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/connection/ConnectionScreen.kt#ConnectionScreen
 * Settings is not drawn: its header (FloatingFrame's top and large title) has no scrollBehavior, so it cannot fold the
 * same way without a new mechanism.
 * not ported: EnterAlwaysScrollBehavior's flingAnimationSpec and snapAnimationSpec (a still picture, no fling)
 * not ported: DiagnosticsDraftScreen's checking state and the FeatherRing (LinkedOut keeps its own loading animation),
 * OverallBanner, InstanceCard, StatusDot, DraftServer and draftServers (MTGA's server list: LinkedOut has one host, so
 * the rows are ConnectionScreen's own host card, its sentence and its selector line, repeated so the list is long enough
 * to scroll), the bar's Add and Re-check actions (the Connection bar has Back only), ConnectionScreen's HostThrottle
 * and Koin (the cooldown is fixed text).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ScrollFullScreenDraftScreenshotTest : ScreenshotBase() {

    // The bar's height is measured after the first frame, and the offset follows it one frame later: frames are given.
    private fun settle() {
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
    }

    @Test
    fun folded() = capture("scroll_full_screen_draft", interact = ::settle) {
        ConnectionDraftScreen(firstItem = 4, fade = false, foldedBy = 1f)
    }

    @Test
    fun back() = capture("scroll_full_screen_draft_back", interact = ::settle) {
        ConnectionDraftScreen(firstItem = 4, fade = false, foldedBy = 0.5f)
    }

    // Edge: the capsule fully folded, at 200 % text and on a 360 dp phone.
    @Test
    fun foldedEdge() = captureEdge("scroll_full_screen_draft", interact = ::settle) {
        ConnectionDraftScreen(firstItem = 4, fade = false, foldedBy = 1f)
    }
}

/** One row of the draft: a host card with its state line, as ConnectionScreen's first block draws it. */
private data class DraftHost(val host: String, val state: String)

private val draftHosts = listOf(
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Pausing for 12 more seconds after a rate limit"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Pausing for 4 more seconds after a rate limit"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Pausing for 30 more seconds after a rate limit"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Ready"),
    DraftHost("www.linkedin.com", "Ready")
)

/**
 * ConnectionScreen with fixed data. [firstItem] scrolls the list that many rows down; [fade] keeps the list's soft edge
 * under the top bar (underFloatingTopBar), off for the full screen scroll of point 26; [foldedBy] is how far the bar
 * has folded away, as enterAlwaysScrollBehavior's heightOffset runs from 0 (in place) to heightOffsetLimit (fully
 * folded): 1 is fully folded, 0 in place. Material's own state did not settle in Robolectric's frames in MTGA's draft
 * (the bar stayed out of the picture at half), so the offset is set on the bar's layer, as there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDraftScreen(firstItem: Int = 0, fade: Boolean = true, foldedBy: Float? = null) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FloatingTopBar(
                modifier = Modifier.graphicsLayer { translationY = -(foldedBy ?: 0f) * size.height },
                title = { BarTitle("Connection") },
                navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onClick = {}) }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstItem)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(
                if (fade) Modifier.underFloatingTopBar(
                    barHeight = padding.calculateTopPadding(),
                    scrolled = listState::scrolledFromTop
                ) else Modifier
            ),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                bottom = navigationBarBottom()
            ).plus(padding)
        ) {
            items(draftHosts.size, key = { it }) { index ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HostCard(draftHosts[index])
                    if (index % 4 == 0) {
                        Text(
                            text = "LinkedOut contacts this one host and nothing else. " +
                                "Pages are read as a logged out visitor, so what you see here " +
                                "is what anyone would see without an account.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Profile selector set 7",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCard(row: DraftHost) {
    ZoneSurface(Modifier.fillMaxWidth(), shape = ShapeDefaults.Large) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(row.host, style = MaterialTheme.typography.titleMedium)
            Text(row.state, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
