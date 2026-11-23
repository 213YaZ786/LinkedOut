package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.EmptyZone
import com.linkedout.app.ui.component.FeedBanner
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (MTGA's requests.md point 18, validated there 2026-11-22), nothing of app/src/main changes:
 * Home with nobody followed. home_empty_linkedout_now is today's top: the centre of the banner is the word "Home" and
 * nothing on either side (TimelineScreen's `followedCount == 0` branch; its alert only shows when something failed, not
 * drawn here). home_empty_linkedout_draft puts the feed switcher as when accounts are followed: the glass capsule
 * (AppBarWithSearch) with the FolderSwitch "All" at the start (its menu: All, Liked, Archived), the middle hint "Search
 * saved posts" hidden while nobody is followed, no alert unless a failure, then the same empty zone.
 * The screen is TimelineScreen's `followedCount == 0` branch (banner, then EmptyState weighted over what is left), a
 * faithful copy with fixed data, the kit's own pieces (FeedBanner, AppBarWithSearch, FolderSwitch, EmptyZone) as
 * TopCapsuleDraftScreenshotTest draws them:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/HomeEmptyDraftScreenshotTest.kt#HomeEmptyDraftScreenshotTest
 * not ported: TimelineScreen's view model, the feed menu (GlassMenu, drawn open in HomeTopDraftScreenshotTest), the
 * failure dialog, the pull to refresh, the posts list, the footer, the challenge pill, the back to top button
 * not ported: MTGA's MenuPill and its feed pill (LinkedOut's top is the capsule of MTGA's point 23: AppBarWithSearch with
 * FolderSwitch)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class HomeEmptyLinkedOutDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun now() = capture("home_empty_linkedout_now") { NowScreen() }

    @Test
    fun draft() = capture("home_empty_linkedout_draft") { DraftScreen() }

    // Edge: the draft at 200 % text and on a 360 dp phone (font scale as Roborazzi's captureRoboImage, device by qualifiers as
    // Now in Android's captureMultiDevice)
    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    // https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt#captureMultiDevice
    @Test
    fun edge() = captureEdge("home_empty_linkedout_draft") { DraftScreen() }

    // Today's banner: only the word "Home" in the centre.
    @Composable
    private fun NowScreen() {
        Screen {
            FeedBanner(
                centre = {
                    Text(
                        "Home",
                        style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            )
        }
    }

    // The draft: the capsule with the folder switch alone, as TimelineScreen's capsule without its hint and its alert.
    @Composable
    private fun DraftScreen() {
        Screen {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AppBarWithSearch(
                    inputField = {},
                    navigationIcon = {
                        // The heading for TalkBack, the switch inside stays its own button (the menu is not drawn:
                        // Robolectric draws no Popup).
                        Box(Modifier.semantics { heading(); contentDescription = "Home" }) {
                            FolderSwitch(
                                icon = LinkedOutIcons.Folder,
                                trailingIcon = LinkedOutIcons.ExpandMore,
                                label = "All",
                                expanded = false,
                                onClick = {}
                            )
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun Screen(banner: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = LocalReadableInset.current)) {
            banner()
            // The weight gives it the rest of the screen, so it sits in the middle of what is left under the banner.
            EmptyZone(
                title = "Nothing followed yet",
                message = "Find a few accounts and they will all appear here in one stream.",
                actionLabel = "Open Accounts",
                onAction = {},
                modifier = Modifier.weight(1f).fillMaxSize()
            )
        }
    }
}
