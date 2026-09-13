package com.linkedout.app.feature.settings

import com.linkedout.app.ui.component.LocalDockPadding
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.linkedout.app.sync.NewPostNotifier
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.linkedout.app.BuildConfig
import com.linkedout.app.data.settings.StartTab
import com.linkedout.app.data.settings.ThemeMode
import com.linkedout.app.ui.theme.TEXT_SCALES
import com.linkedout.app.ui.theme.textScaleLabel
import org.koin.androidx.compose.koinViewModel

private enum class OpenDialog { NONE, THEME, TEXT_SIZE, KEEP, FREQUENCY, CLEAR, START_TAB }

private val KEEP_DAYS = listOf(7, 30, 90, 365, 0)

private fun keepLabel(days: Int): String = when (days) {
    0 -> "Forever"
    7 -> "One week"
    30 -> "One month"
    90 -> "Three months"
    365 -> "One year"
    else -> "$days days"
}

/**
 * Settings, grouped by what people come here to change. Every description
 * says what the option does for the reader, in plain words.
 */
@Composable
fun SettingsScreen(
    onOpenDebugLog: () -> Unit,
    onOpenWelcome: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val storageBytes by viewModel.storageBytes.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Changed in system settings, so read again each time the screen returns.
    var linkedInLinksOn by remember { mutableStateOf(linkedInLinksEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        linkedInLinksOn = linkedInLinksEnabled(context)
        onPauseOrDispose { }
    }
    var dialog by remember { mutableStateOf(OpenDialog.NONE) }

    LaunchedEffect(Unit) { viewModel.measureStorage() }

    // The system file picker, so no storage permission is ever needed.
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportAccounts) }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importAccounts) }

    // Notifications can be blocked in Android at any time, so this is read
    // again whenever the screen comes back, like the LinkedIn links state.
    val notifier = remember { NewPostNotifier(context) }
    var notificationsAllowed by remember { mutableStateOf(notifier.canNotify()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notifier.canNotify()
        onPauseOrDispose { }
    }
    // Asked at the moment the reader turns notifications on, never before.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = notifier.canNotify()
        if (granted && notificationsAllowed) {
            viewModel.setNotifyNewPosts(true)
        } else {
            // Refused now or earlier. Android will not ask twice, so its own
            // screen is the only way left, and the reader decides there.
            Toast.makeText(context, "Notifications are off for LinkedOut in Android", Toast.LENGTH_SHORT).show()
            openNotificationSettings(context)
        }
    }
    val onNotifyChange: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> viewModel.setNotifyNewPosts(false)
            notifier.canNotify() -> viewModel.setNotifyNewPosts(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                // Permission granted or not needed, but switched off in Android.
                Toast.makeText(context, "Notifications are off for LinkedOut in Android", Toast.LENGTH_SHORT).show()
                openNotificationSettings(context)
            }
        }
    }

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp)
        )

        Section("Appearance") {
            SettingRow(
                title = "Theme",
                summary = themeLabel(settings.themeMode),
                onClick = { dialog = OpenDialog.THEME }
            )
            SwitchRow(
                title = "Pure black",
                summary = "Deeper blacks in dark mode. Easier on the battery with OLED screens.",
                checked = settings.pureBlack,
                enabled = settings.themeMode != ThemeMode.LIGHT,
                onChange = viewModel::setPureBlack
            )
            SwitchRow(
                title = "Show counts",
                summary = "Replies, reposts, likes and views under each post.",
                checked = settings.showCounts,
                onChange = viewModel::setShowCounts
            )
            SettingRow(
                title = "Text size",
                summary = textScaleLabel(settings.textScale) + ", on top of Android's font size",
                onClick = { dialog = OpenDialog.TEXT_SIZE }
            )
            SwitchRow(
                title = "Compact posts",
                summary = "Tighter spacing, smaller avatars and shorter pictures. More posts on screen.",
                checked = settings.compactPosts,
                onChange = viewModel::setCompactPosts
            )
            SwitchRow(
                title = "Square avatars",
                summary = "Rounded squares instead of circles.",
                checked = settings.squareAvatars,
                onChange = viewModel::setSquareAvatars
            )
        }

        Section("Media") {
            SwitchRow(
                title = "Media on Wi-Fi only",
                summary = "On mobile data, pictures and videos wait for a tap. Avatars still load.",
                checked = settings.mediaOnWifiOnly,
                onChange = viewModel::setMediaOnWifiOnly
            )
            SwitchRow(
                title = "Play videos automatically",
                summary = "Videos and GIFs play silently in the list as you scroll, and start " +
                    "when opened. Never on mobile data while Wi-Fi only is on.",
                checked = settings.autoplayVideos,
                onChange = viewModel::setAutoplayVideos
            )
            SwitchRow(
                title = "Start videos muted",
                summary = "Sound stays off until you tap the speaker.",
                checked = settings.startMuted,
                onChange = viewModel::setStartMuted
            )
        }

        Section("Reading") {
            SettingRow(
                title = "Start on",
                summary = startTabLabel(settings.startTab),
                onClick = { dialog = OpenDialog.START_TAB }
            )
            SettingRow(
                title = "Open LinkedIn links in LinkedOut",
                summary = if (linkedInLinksOn) {
                    "On. linkedin.com and lnkd.in links open here."
                } else {
                    "Off. Turn on \"Open supported links\" and add the linkedin.com " +
                        "links. Sharing a link to LinkedOut works either way."
                },
                onClick = { openLinkSettings(context) }
            )
        }

        Section("Background updates") {
            SwitchRow(
                title = "Check for new posts",
                summary = "Keeps Home up to date and saves new posts, even when the app is closed.",
                checked = settings.backgroundSync,
                onChange = viewModel::setBackgroundSync
            )
            SettingRow(
                title = "Frequency",
                summary = intervalLabel(settings.syncIntervalMinutes),
                enabled = settings.backgroundSync,
                onClick = { dialog = OpenDialog.FREQUENCY }
            )
            SwitchRow(
                title = "Wi-Fi only",
                summary = "Uses no mobile data for background checks.",
                checked = settings.syncOnWifiOnly,
                enabled = settings.backgroundSync,
                onChange = viewModel::setWifiOnly
            )
            SwitchRow(
                title = "Notify about new posts",
                summary = when {
                    !settings.backgroundSync -> "Needs \"Check for new posts\"."
                    settings.notifyNewPosts && !notificationsAllowed ->
                        "Blocked in Android settings. Tap to allow."
                    else -> "A notification for each new post Home would show, grouped, one sound per check."
                },
                checked = settings.notifyNewPosts && notificationsAllowed,
                enabled = settings.backgroundSync,
                // Shown off while Android blocks it, so a tap then means
                // "make it work" and leads to the permission or Android's page.
                onChange = onNotifyChange
            )
        }

        Section("Data") {
            SettingRow(
                title = "Keep posts",
                summary = keepLabel(settings.keepPostsDays) +
                    ". Older posts can still be read by scrolling back.",
                onClick = { dialog = OpenDialog.KEEP }
            )
            SettingRow(
                title = "Export accounts",
                summary = "Save the accounts you follow to a file, to keep or to move to another phone.",
                onClick = { exporter.launch("linkedout-accounts.json") }
            )
            SettingRow(
                title = "Import accounts",
                summary = "From a LinkedOut export, or a text file of profile addresses, one per line.",
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            )
            SettingRow(
                title = "Saved posts",
                summary = storageBytes?.let {
                    "${Formatter.formatShortFileSize(context, it)} on this phone. Readable offline."
                } ?: "Measuring",
                onClick = null
            )
            SettingRow(
                title = "Clear saved posts",
                summary = "Frees space. The accounts you follow are kept.",
                onClick = { dialog = OpenDialog.CLEAR }
            )
        }

        Section("Help") {
            SettingRow(
                title = "Review the tutorial",
                summary = "Where a profile address is, and the ways to follow someone.",
                onClick = onOpenWelcome
            )
            SettingRow(
                title = "Activity log",
                summary = "Technical details to share when you report a problem.",
                onClick = onOpenDebugLog
            )
        }

        Section("About") {
            SettingRow(
                title = "LinkedOut ${BuildConfig.VERSION_NAME}",
                summary = "Read public LinkedIn posts with no account, no tracking and no ads.",
                onClick = null
            )
            SettingRow(
                title = "Source code",
                summary = "github.com/213YaZ786/LinkedOut",
                onClick = { uriHandler.openUri("https://github.com/213YaZ786/LinkedOut") }
            )
            SettingRow(
                title = "Reads",
                summary = "One host, www.linkedin.com, as a guest. No account, no API key.",
                onClick = null
            )
        }

        Spacer(Modifier.height(24.dp + LocalDockPadding.current))
    }

    when (dialog) {
        OpenDialog.START_TAB -> ChoiceDialog(
            title = "Start on",
            options = StartTab.entries.map { it to startTabLabel(it) },
            selected = settings.startTab,
            onSelect = viewModel::setStartTab,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = viewModel::setTheme,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = "Text size",
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onSelect = viewModel::setTextScale,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.KEEP -> ChoiceDialog(
            title = "Keep posts",
            options = KEEP_DAYS.map { it to keepLabel(it) },
            selected = settings.keepPostsDays,
            onSelect = viewModel::setKeepPostsDays,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.FREQUENCY -> ChoiceDialog(
            title = "Check for new posts",
            options = INTERVALS.map { it to intervalLabel(it) },
            selected = settings.syncIntervalMinutes,
            onSelect = viewModel::setInterval,
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.CLEAR -> AlertDialog(
            onDismissRequest = { dialog = OpenDialog.NONE },
            title = { Text("Clear saved posts?") },
            text = {
                Text("Posts saved on this phone will be deleted. They load again the next time you open an account.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSavedPosts()
                    dialog = OpenDialog.NONE
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = OpenDialog.NONE }) { Text("Cancel") }
            }
        )
        OpenDialog.NONE -> Unit
    }
}

/** A titled group of rows on one rounded card, the Obtainium way. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp)
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = summary?.let {
            { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) }
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun startTabLabel(tab: StartTab): String = when (tab) {
    StartTab.HOME -> "Home"
    StartTab.ACCOUNTS -> "Accounts"
    StartTab.LAST -> "Last tab used"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private val INTERVALS = listOf(15, 30, 60, 180, 360)

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60 -> "Every hour"
    else -> "Every ${minutes / 60} hours"
}

/**
 * Whether Android sends x.com links to LinkedOut. They can only be enabled by the
 * reader, since only X could verify the domain, so this reads the reader's
 * choice rather than a verification.
 */
/**
 * True when the reader has allowed LinkedOut to open LinkedIn links. The keys
 * of hostToStateMap are the hosts the manifest declares, wildcard included, so
 * this asks for the exact strings written there and not for a concrete
 * subdomain like www.linkedin.com, which would never be a key.
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

/** The system screen where supported links are switched on for this app. */
/** LinkedOut's own notification page in Android settings. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openLinkSettings(context: Context) {
    val intent = Intent(
        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}
