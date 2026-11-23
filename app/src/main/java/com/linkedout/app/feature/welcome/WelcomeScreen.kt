/*
 * The welcome guide as tiles, after FlorisBoard's SetupScreen: every step is read from the system or the stores
 * each time the screen comes back, shown done or not, and the tiles are Modules' SetupTiles. Ported from the same
 * guide of Language, the first app whose guide is tiles
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt
 *
 * Copyright (C) 2021-2025 The FlorisBoard Contributors, Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0), translated from
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 *
 * not ported: SetupScreen (it is WelcomeScreen and WelcomeContent here), SetupLinks (FlorisBoard's own links to its
 * site), awaitTransition (the wait for the keyboard to be enabled, which this guide has no step for), Flow (the type
 * awaitTransition extends)
 */
package com.linkedout.app.feature.welcome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.linkedout.app.core.system.BatteryExemption
import com.linkedout.app.core.update.StoreLink
import com.linkedout.app.core.update.UpdateMode
import com.linkedout.app.core.update.Updates
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.data.settings.Settings
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.feature.settings.SettingsIcons
import com.linkedout.app.feature.settings.SettingsViewModel
import com.linkedout.app.feature.settings.openNotificationSettings
import com.linkedout.app.sync.NewPostNotifier
import com.linkedout.app.ui.component.ChoiceDialog
import com.linkedout.app.ui.component.LoadingMark
import com.linkedout.app.ui.component.SetupTile
import com.linkedout.app.ui.component.SetupTileDialog
import com.linkedout.app.ui.component.SetupTileGrid
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** The choice dialog the screen has open, as Settings' OpenDialog does. */
private enum class OpenDialog { NONE, MEDIA, UPDATES, NOTIFY }

/**
 * What the tiles and the screen share: the tiles, the dialog a tile opened, and the picks that dialog sends back.
 * Kept apart from [rememberSetupTiles] so that Settings can ask for the tiles alone, as it counts what is left.
 */
private class Setup(
    val tiles: List<SetupTile>,
    val dialog: MutableState<OpenDialog>,
    val settings: Settings,
    val chooseMedia: (AutoDownload) -> Unit,
    val chooseUpdates: (UpdateMode) -> Unit,
    val chooseNotify: (Boolean) -> Unit,
    val notifyOn: Boolean
)

/**
 * The few choices worth making, the best one already set, as tiles: each is read from the stores and the system
 * again each time the screen comes back (SetupScreen reads isFlorisBoardEnabled, isFlorisBoardSelected and
 * hasNotificationPermission, and sets the current step from them), and turns done by itself.
 * Ported from rememberSetupTiles of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 * Only the choices that weigh on the reader are tiles (the user, 2026-11-06): what spends their data (saving
 * media), a permission (installing updates), work with the app closed (new posts), a role of the phone (LinkedIn
 * links). Each starts on the best setting (Never, Install, Off, as the system has it), so none is required and
 * Start never waits. Following someone is an action of the Accounts tab, not a choice, so it is no tile.
 */
@Composable
fun rememberSetupTiles(): List<SetupTile> = rememberSetup().tiles

@Composable
private fun rememberSetup(): Setup {
    val context = LocalContext.current
    val store: SettingsStore = koinInject()
    val sync: SettingsViewModel = koinViewModel()
    val current by store.settings.collectAsState()
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val dialog = rememberSaveable { mutableStateOf(OpenDialog.NONE) }

    val canInstall = remember(checks) { Updates.canInstall(context) }
    val viaStore = remember(checks) { StoreLink.viaStore(context) }
    // The notification state is NewPostNotifier's own check, so the guide agrees with what a check would do.
    val notifier = remember { NewPostNotifier(context) }
    val canNotify = remember(checks) { notifier.canNotify() }
    val exempt = remember(checks) { BatteryExemption.isExempt(context) }
    val linksOn = remember(checks) { linkedInLinksEnabled(context) }
    val notifyOn = current.backgroundSync && current.notifyNewPosts
    val hasNotificationPermission = remember(checks) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    // Refused when the reader was asked and said no: it stays so, shown on the tile, until Android allows it.
    var notifyRefused by rememberSaveable { mutableStateOf(false) }
    // Save media's own refusal, as notifyRefused is New posts': set when the permission came back refused.
    var mediaRefused by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (notifier.canNotify()) mediaRefused = false
        onPauseOrDispose { }
    }
    val askDownloadNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        checks++
        // The error of Save media shows only once the reader was asked and said no, never before.
        if (!granted && !notifier.canNotify()) mediaRefused = true
    }

    val chooseMedia: (AutoDownload) -> Unit = { choice ->
        // The watermark back to zero, as Settings does: the first pass saves what is on screen now.
        store.update { it.copy(autoDownloadMedia = choice, autoDownloadedUntilMillis = 0) }
        // The progress of a download is a notification like any other, and Android 13 and later want the
        // permission for it. Asked here because here is where the reader asked for downloads.
        if (choice != AutoDownload.NEVER && !hasNotificationPermission) {
            askDownloadNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val chooseUpdates: (UpdateMode) -> Unit = { mode ->
        store.update { it.copy(updates = mode) }
        if (mode == UpdateMode.INSTALL && !canInstall) Updates.allowInstalls(context)
    }
    val turnOnNotify = {
        notifyRefused = false
        sync.setBackgroundSync(true)
        sync.setNotifyNewPosts(true)
        // Checks run with the app closed: see BatteryExemption.
        BatteryExemption.request(context)
    }
    // Allowed on Android's notification page after a refusal: the reader asked for New posts, so it turns on.
    LifecycleResumeEffect(Unit) {
        if (notifyRefused && notifier.canNotify()) turnOnNotify()
        onPauseOrDispose { }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Shown on the tile instead of silently left off: its tap opens Android's notification page.
        if (granted) turnOnNotify() else notifyRefused = true
        checks++
    }
    val chooseNotify: (Boolean) -> Unit = { on ->
        when {
            !on -> {
                notifyRefused = false
                sync.setNotifyNewPosts(false)
            }
            !hasNotificationPermission -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> turnOnNotify()
        }
    }

    // Saving shows its progress as a notification: refused, the tile turns to an error and its tap opens Android's
    // notification page. New posts likewise, and when the system may hold its checks back its tap asks again.
    val mediaBlocked = current.autoDownloadMedia != AutoDownload.NEVER && !canNotify && mediaRefused
    val notifyBlocked = notifyRefused && !canNotify
    val notifyLate = notifyOn && canNotify && !exempt
    val tiles = listOf(
        SetupTile(
            id = "media", name = "Save media", title = "Save media", icon = LinkedOutIcons.Download,
            state = if (mediaBlocked) "Saved, no progress bar" else autoDownloadLabel(current.autoDownloadMedia),
            summary = if (mediaBlocked) {
                "Android blocks this app's notifications, so a saving shows no progress bar. Tap to open its notification page."
            } else {
                "Posts saved on the phone open again offline. It is the one setting that spends your data, " +
                    "off until you choose otherwise."
            },
            done = true, required = false,
            error = mediaBlocked,
            onClick = { if (mediaBlocked) openNotificationSettings(context) else dialog.value = OpenDialog.MEDIA }
        ),
        SetupTile(
            id = "updates", name = "Updates", title = "Updates", icon = SettingsIcons.Update,
            state = when {
                viaStore -> "Through Store"
                current.updates == UpdateMode.INSTALL && !canInstall -> "Installs not allowed"
                else -> updatesLabel(current.updates)
            },
            summary = "When a new version is out, LinkedOut can tell you, or install it when you leave the app. " +
                "Android's permission to install is needed once.",
            done = viaStore || current.updates != UpdateMode.INSTALL || canInstall, required = false,
            error = !viaStore && current.updates == UpdateMode.INSTALL && !canInstall,
            onClick = {
                if (!viaStore && current.updates == UpdateMode.INSTALL && !canInstall) Updates.allowInstalls(context)
                else dialog.value = OpenDialog.UPDATES
            }
        ),
        SetupTile(
            id = "notify", name = "New posts", title = "New posts", icon = LinkedOutIcons.Bell,
            // The states and the taps of MTGA's New posts tile
            // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
            state = when {
                notifyBlocked -> "Notifications blocked"
                !notifyOn -> "Off"
                !canNotify -> "Notifications off"
                notifyLate -> "Checks may be late"
                else -> "Notify me"
            },
            summary = when {
                notifyBlocked -> "Android blocks this app's notifications. Tap to open its notification page."
                notifyLate -> "Android may hold the checks back while the app is closed. Tap to allow it."
                else -> "A notification when the accounts you follow post. LinkedOut then checks in the background, " +
                    "which costs battery."
            },
            done = !notifyOn || (canNotify && exempt), required = false,
            error = notifyBlocked || (notifyOn && !canNotify) || notifyLate,
            onClick = {
                when {
                    notifyBlocked -> openNotificationSettings(context)
                    notifyLate -> BatteryExemption.request(context)
                    else -> dialog.value = OpenDialog.NOTIFY
                }
            }
        ),
        SetupTile(
            id = "links", name = "LinkedIn links", title = "LinkedIn links", icon = SettingsIcons.Link,
            state = if (linksOn) "On" else "Off",
            summary = "linkedin.com links tapped anywhere open in LinkedOut once Android's \"Open supported links\" " +
                "is on for it. Tap to open that page.",
            done = linksOn, required = false,
            onClick = { openLinkSettings(context) }
        )
    )
    return Setup(tiles, dialog, current, chooseMedia, chooseUpdates, chooseNotify, notifyOn)
}

/** The labels of Settings' own dialogs, so a choice reads the same here. */
private fun autoDownloadLabel(choice: AutoDownload): String = when (choice) {
    AutoDownload.NEVER -> "Never"
    AutoDownload.WIFI -> "On Wi-Fi only"
    AutoDownload.ALWAYS -> "On Wi-Fi or mobile data"
}

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install"
}

/**
 * The guide: the animated logo, the app's function, the tiles, then Start (Done when reopened from Settings), which
 * never waits: a refusal shows on its own tile. Laid out as SetupScreen: header, steps, the one button.
 * [onFinish] receives true when the reader asks to go to Accounts.
 * Ported from WelcomeScreen of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 * and from SetupScreen#SetupScreen of
 * https://github.com/florisboard/florisboard/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/app/setup/SetupScreen.kt#SetupScreen
 */
@Composable
fun WelcomeScreen(onFinish: (openAccounts: Boolean) -> Unit) {
    val store: SettingsStore = koinInject()
    val followed: AccountStore = koinInject()
    // Read once: closing the guide sets welcomeSeen, and the button must not turn to Done while it leaves.
    val reopened = remember { store.current.welcomeSeen }
    val setup = rememberSetup()
    val steps = setup.tiles
    // The tile whose long press is open (its id), "" when none.
    var explained by rememberSaveable { mutableStateOf("") }
    val close = { setup.dialog.value = OpenDialog.NONE }

    // Start lands on Accounts while nobody is followed, where an address is pasted (the old guide's last Next).
    WelcomeContent(steps, reopened, onStart = { onFinish(followed.accounts.value.isEmpty()) }) { explained = it.id }

    steps.firstOrNull { it.id == explained }?.let { tile ->
        SetupTileDialog(tile, onDismiss = { explained = "" })
    }

    when (setup.dialog.value) {
        OpenDialog.NONE -> Unit
        OpenDialog.MEDIA -> ChoiceDialog(
            title = "Save media",
            options = AutoDownload.entries.map { it to autoDownloadLabel(it) },
            selected = setup.settings.autoDownloadMedia,
            icon = LinkedOutIcons.Download,
            onPick = { close(); setup.chooseMedia(it) },
            onDismiss = close
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = setup.settings.updates,
            icon = SettingsIcons.Update,
            onPick = { close(); setup.chooseUpdates(it) },
            onDismiss = close
        )
        OpenDialog.NOTIFY -> ChoiceDialog(
            title = "New posts",
            options = listOf(false to "Off", true to "Notify me"),
            selected = setup.notifyOn,
            icon = LinkedOutIcons.Bell,
            onPick = { close(); setup.chooseNotify(it) },
            onDismiss = close
        )
    }
}

/**
 * What the guide draws from the tiles it is given: the layout of SetupScreen (header, steps, footer with the one
 * button), split from [WelcomeScreen] so that a picture can be taken with fixed tiles, without the system checks.
 * The block is centred in the window's height, as WhatsNewKit's WhatsNewView does, and Start is the cookie of
 * [StartCookie] 40 dp under the tiles, with nothing under it. Ported from WelcomeContent of MTGA's guide
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/welcome/WelcomeScreen.kt#WelcomeContent
 * with the centred content block of
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views (the
 * app's title and SetupTileGrid take their place), the pinned footer (Start ends the block).
 */
@Composable
internal fun WelcomeContent(tiles: List<SetupTile>, reopened: Boolean, onStart: () -> Unit, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
    // WhatsNewView's body: the scrolling content is a block centred in the window when shorter than it (the viewport is
    // the minimum height of the block). Drawn over the whole window, so it keeps clear of the system bars itself.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                // The icon's own animation, as the guide played it before the tiles (the user, 2026-11-07).
                LoadingMark(size = 160.dp)
                Spacer(Modifier.height(16.dp))
                // The mark, one line saying what the app is, the tiles, Start: no title, no "Welcome", no "N left",
                // each tile shows its own check and its own refusal, none is required.
                Text(
                    "LinkedIn reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(steps, onExplain)
                Spacer(Modifier.height(40.dp))
                StartCookie(description = if (reopened) "Done" else "Start", onClick = onStart)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * True when the reader has allowed LinkedOut to open LinkedIn links. The keys of hostToStateMap are the hosts the
 * manifest declares, wildcard included, so this asks for the exact strings written there and not for a concrete
 * subdomain like www.linkedin.com, which would never be a key. A private copy of
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/settings/SettingsScreen.kt#linkedInLinksEnabled
 */
private fun linkedInLinksEnabled(context: Context): Boolean {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }
        .getOrNull() ?: return false
    if (!state.isLinkHandlingAllowed) return false
    return listOf("linkedin.com", "*.linkedin.com", "lnkd.in").any { host ->
        val hostState = state.hostToStateMap[host]
        hostState == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
            hostState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
    }
}

/**
 * The system screen where supported links are switched on for this app. A private copy of
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/settings/SettingsScreen.kt#openLinkSettings
 */
private fun openLinkSettings(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
