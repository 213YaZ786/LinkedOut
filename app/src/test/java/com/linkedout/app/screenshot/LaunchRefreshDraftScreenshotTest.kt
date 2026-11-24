package com.linkedout.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.feature.settings.SettingsIcons
import com.linkedout.app.ui.component.DialogPane
import com.linkedout.app.ui.component.GlassSlider
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.SettingsActionTile
import com.linkedout.app.ui.component.SettingsSectionTitle
import com.linkedout.app.ui.component.SettingsTileGrid
import com.linkedout.app.ui.component.SyncFrequency
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 40), nothing of app/src/main changes: the setting "Refresh at launch" as a
 * tile of Settings' Reading section, the same TileFace and size as Start on and LinkedIn links, its value the tile's
 * state ("1 h", "Every launch", "Never"). A tap opens a dialog as SettingsScreen's other choices do (ZoneAlertDialog's
 * frame: title, the value as text, one button, Done) holding the app's GlassSlider with nine stops, the stops being the
 * app's own SyncFrequency (ui/component/LaunchRefresh.kt: its tile for the state, its line for the text).
 * Pictures: launch_refresh_draft_tile (the section at the default 1 h), launch_refresh_draft_dialog_1h and
 * launch_refresh_draft_dialog_never (the dialog over the section), and the tile at 200 % text and on a 360 dp phone.
 * The stops are Feeder's SyncFrequency (15 min, 30 min, 1 h, 3 h, 6 h, 12 h, 1 day, manual) with Every launch first:
 * https://github.com/spacecowboy/Feeder/blob/master/app/src/main/java/com/nononsenseapps/feeder/archmodel/SettingsStore.kt#SyncFrequency
 * Ported from MTGA's draft of the same point
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/LaunchRefreshDraftScreenshotTest.kt#LaunchRefreshDraftScreenshotTest
 * and the dialog's frame is ZoneAlertDialog's
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ZoneDialog.kt#ZoneAlertDialog
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/GlassSlider.kt#GlassSlider
 * not ported: SyncFrequency's stringId (plain text here), MANUAL's minutes -1 (Never takes the last stop),
 * setSyncFrequency's storage and schedulePeriodicRssSync (a draft keeps nothing, nothing runs in the background);
 * MTGA's own Stop enum (this app's SyncFrequency takes its place), its Share as Nitter and X links tiles (this app has
 * LinkedIn links only); ZoneAlertDialog's window and the capture's `window = true` (this base's capture draws the
 * activity only, a Dialog is a window of its own: the pane is DialogPane, as NameFieldDraftScreenshotTest draws its
 * dialogs, with ZoneAlertContent's 24 dp layout, over a scrim standing for the blurred screen)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class LaunchRefreshDraftScreenshotTest : ScreenshotBase() {

    // The Reading section's tiles, as SettingsScreen builds them (fixed values), the new one among them.
    private fun tiles(stop: SyncFrequency) = listOf(
        SettingsActionTile(
            id = "startTab", name = "Start on", title = "Start on", icon = LinkedOutIcons.Home,
            value = "Home", summary = "Home"
        ),
        SettingsActionTile(
            id = "refreshLaunch", name = "Refresh at launch", title = "Refresh at launch", icon = SettingsIcons.Timer,
            value = stop.tile, summary = stop.line
        ),
        SettingsActionTile(
            id = "linkedInLinks", name = "LinkedIn links", title = "Open LinkedIn links in LinkedOut", icon = SettingsIcons.Link,
            value = "Off",
            summary = "Off. Turn on \"Open supported links\" and add the linkedin.com " +
                "links. Sharing a link to LinkedOut works either way."
        )
    )

    @Test
    fun tile() = capture("launch_refresh_draft_tile") { Reading(SyncFrequency.EVERY_1_HOURS, open = false) }

    @Test
    fun dialogOneHour() = capture("launch_refresh_draft_dialog_1h") { Reading(SyncFrequency.EVERY_1_HOURS, open = true) }

    @Test
    fun dialogNever() = capture("launch_refresh_draft_dialog_never") { Reading(SyncFrequency.NEVER, open = true) }

    @Test
    fun edge() = captureEdge("launch_refresh_draft_tile") { Reading(SyncFrequency.EVERY_1_HOURS, open = false) }

    // The Reading section, and over it the dialog a tap on the tile opens.
    @Composable
    private fun Reading(stop: SyncFrequency, open: Boolean) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                SettingsSectionTitle("Reading")
                SettingsTileGrid(tiles(stop)) {}
            }
            if (open) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                    RefreshDialog(stop)
                }
            }
        }
    }
}

/**
 * ZoneAlertDialog's frame without its window: DialogPane, the title, a centred text and its one button; the slider is the
 * choice. The width is the dialog's 280 to 560 dp. Shared by the two drafts of points 40 and 41.
 */
@Composable
internal fun RefreshDialogPane(start: SyncFrequency) {
    var index by remember { mutableFloatStateOf(start.ordinal.toFloat()) }
    val stops = SyncFrequency.entries
    val current = stops[index.toInt().coerceIn(0, stops.lastIndex)]
    Box(Modifier.widthIn(min = 280.dp, max = 560.dp).padding(horizontal = 16.dp)) {
        DialogPane {
            Column(Modifier.padding(24.dp)) {
                Box(Modifier.padding(bottom = 16.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Text("Refresh at launch", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
                // ZoneAlertContent's text slot: bodyMedium merged in, text centred.
                ProvideTextStyle(LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium).copy(textAlign = TextAlign.Center)) {
                    Column(
                        Modifier.padding(bottom = 24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            current.line,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassSlider(
                            value = index,
                            onValueChange = { index = it },
                            valueRange = 0f..(stops.size - 1).toFloat(),
                            steps = stops.size - 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f), propagateMinConstraints = true) { QuietButton(onClick = {}) { Text("Done") } }
                }
            }
        }
    }
}

@Composable
private fun RefreshDialog(stop: SyncFrequency) = RefreshDialogPane(stop)
