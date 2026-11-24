package com.linkedout.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.R
import com.linkedout.app.feature.settings.SettingsIcons
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.SetupTile
import com.linkedout.app.ui.component.SetupTileGrid
import com.linkedout.app.ui.component.SyncFrequency
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 41), nothing of app/src/main changes: the welcome guide as the app draws
 * it today (WelcomeModelDraftScreenshotTest's layout and tiles, MarkStandIn for the animated mark) with one more tile
 * after Save media, "Refresh at launch", the Settings tile's icon (SettingsIcons.Timer), its state the stop's tile label
 * ("1 h"), a preset so done, not required. guide_refresh_draft_dialog shows the dialog the tile opens, the Settings one
 * (LaunchRefreshDraftScreenshotTest's RefreshDialogPane) over the guide, at 1 h.
 * The stops are Feeder's SyncFrequency (the app holds it as ui/component/LaunchRefresh.kt SyncFrequency):
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * Ported from MTGA's draft of the same point
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/GuideRefreshDraftScreenshotTest.kt#GuideRefreshDraftScreenshotTest
 * the guide's layout being LinkedOut's WelcomeModelDraftScreenshotTest.kt#WelcomeContentDraft
 * not ported: SyncFrequency's stringId (plain text here), setSyncFrequency's storage and schedulePeriodicRssSync
 * (a draft keeps nothing, nothing runs in the background); MTGA's own Stop enum (SyncFrequency takes its place), its
 * Sources and X links tiles (LinkedOut's four tiles instead), its LoadingMark (does not draw on Robolectric:
 * MarkStandIn); ZoneAlertDialog's window and the capture's `window = true` (see LaunchRefreshDraftScreenshotTest)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class GuideRefreshDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String, summary: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = summary, done = done,
        required = false, error = error, onClick = onClick
    )

    private val tiles = listOf(
        tile("media", "Save media", LinkedOutIcons.Download, "Never", "", done = true),
        tile("refresh", "Refresh at launch", SettingsIcons.Timer, SyncFrequency.EVERY_1_HOURS.tile,
            "When the app opens, an account is read again once its last read is older than this. Tap to choose.", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", "", done = true),
        tile("notify", "New posts", LinkedOutIcons.Bell, "Off", "", done = true),
        tile("links", "LinkedIn links", SettingsIcons.Link, "Off", "", done = false)
    )

    @Test
    fun draft() = capture("guide_refresh_draft") { Guide(open = false) }

    @Test
    fun dialog() = capture("guide_refresh_draft_dialog") { Guide(open = true) }

    @Test
    fun edge() = captureEdge("guide_refresh_draft") { Guide(open = false) }

    @Composable
    private fun Guide(open: Boolean) {
        Box(Modifier.fillMaxSize()) {
            GuideContentDraft(tiles)
            if (open) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                    RefreshDialogPane(SyncFrequency.EVERY_1_HOURS)
                }
            }
        }
    }
}

// LoadingMark does not draw on Robolectric, so its two bitmaps stand in at 160 dp (WelcomeModelDraftScreenshotTest).
@Composable
private fun GuideMarkStandIn() {
    val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
    val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
    Box(Modifier.size(160.dp)) {
        Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** The guide's layout as WelcomeModelDraftScreenshotTest draws it (WelcomeContentDraft): the mark, a line, the tiles, Start. */
@Composable
private fun GuideContentDraft(tiles: List<SetupTile>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(40.dp))
            GuideMarkStandIn()
            Spacer(Modifier.height(20.dp))
            Text(
                "LinkedIn reader",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(24.dp))
            SetupTileGrid(tiles) {}
            Spacer(Modifier.height(32.dp))
            BoldButton(filled = true, onClick = {}) { Text("Start") }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
