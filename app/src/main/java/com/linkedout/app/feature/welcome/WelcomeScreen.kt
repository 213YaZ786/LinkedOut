package com.linkedout.app.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.linkedout.app.data.settings.AutoDownload
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val points: List<String>,
    /** Shows a profile address with the part LinkedOut needs picked out. */
    val showLinkExample: Boolean = false,
    /** The last page asks for a decision instead of explaining anything. */
    val showMediaChoice: Boolean = false
)

/**
 * Four pages, and every line in them is something the reader cannot work out
 * on their own. What this reads, where the address is, how to follow, and the
 * one decision that costs their data. Nothing about what the app is for and
 * nothing it could show instead of saying.
 */
private val PAGES = listOf(
    WelcomePage(
        icon = LinkedOutIcons.Home,
        title = "LinkedOut",
        intro = "Public LinkedIn posts, without an account.",
        points = listOf(
            "The accounts you follow stay on this phone.",
            "LinkedIn shows a guest a few profiles at a time. Some reads are refused, " +
                "and trying again later works."
        )
    ),
    WelcomePage(
        icon = LinkedOutIcons.Person,
        title = "An address, not a name",
        intro = "LinkedIn has no @handle, and searching by name needs an account.",
        points = listOf(
            "Open the profile in a browser or in the LinkedIn app and copy its address.",
            "Companies and schools work the same way, with /company/ or /school/."
        ),
        showLinkExample = true
    ),
    WelcomePage(
        icon = LinkedOutIcons.Folder,
        title = "Follow and file",
        intro = "Paste the address in Accounts, then Follow.",
        points = listOf(
            "Sharing a profile from another app to LinkedOut does the same.",
            "Folders in Accounts give one stream each. Home switches between them from its title."
        )
    ),
    WelcomePage(
        icon = LinkedOutIcons.Download,
        title = "Saving media",
        intro = "Pictures and videos can be kept on the phone, so a post read once " +
            "opens again with no connection.",
        points = listOf(
            "It costs space and, on mobile data, data.",
            "Changeable at any time in Settings, under Media."
        ),
        showMediaChoice = true
    )
)

/**
 * The guide shown on first launch, and again from Settings.
 *
 * The last page asks for the media decision rather than explaining it, and
 * the way out is that choice. It is the only setting that spends the reader's
 * data without being asked again, so it is not left as a default they never
 * saw. Skip still skips: someone reopening this from Settings is not made to
 * answer twice.
 *
 * [onFinish] receives true when the reader asks to go to Accounts.
 */
@Composable
fun WelcomeScreen(onFinish: (openAccounts: Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    val settings: SettingsStore = koinInject()
    val context = LocalContext.current
    var chosen by remember { mutableStateOf<AutoDownload?>(null) }
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val choose: (AutoDownload) -> Unit = { choice ->
        chosen = choice
        settings.update { it.copy(autoDownloadMedia = choice) }
        // The progress of a download is a notification like any other, and
        // Android 13 and later want the permission for it. Asked here because
        // here is where the reader asked for downloads.
        if (choice != AutoDownload.NEVER &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onFinish(false) }) { Text(if (last) "Close" else "Skip") }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index -> PageContent(page = PAGES[index], chosen = chosen, onChoose = choose) }

        Dots(count = PAGES.size, current = pager.currentPage)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text("Back")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                // The last page has no way forward until the choice is made.
                enabled = !last || chosen != null,
                onClick = {
                    if (last) onFinish(true) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            ) { Text(if (last) "Go to Accounts" else "Next") }
        }
    }
}

@Composable
private fun PageContent(
    page: WelcomePage,
    chosen: AutoDownload?,
    onChoose: (AutoDownload) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            page.intro,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (page.showLinkExample) LinkExample()
        if (page.showMediaChoice) MediaChoice(chosen = chosen, onChoose = onChoose)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            page.points.forEach { Point(it) }
        }
    }
}

/** Three rows, one of which has to be tapped before the guide can be left. */
@Composable
private fun MediaChoice(chosen: AutoDownload?, onChoose: (AutoDownload) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MediaOption("Only when I open them", AutoDownload.NEVER, chosen, onChoose)
        MediaOption("Save them on Wi-Fi", AutoDownload.WIFI, chosen, onChoose)
        MediaOption("Save them on any network", AutoDownload.ALWAYS, chosen, onChoose)
    }
}

@Composable
private fun MediaOption(
    label: String,
    value: AutoDownload,
    chosen: AutoDownload?,
    onChoose: (AutoDownload) -> Unit
) {
    val picked = chosen == value
    Surface(
        onClick = { onChoose(value) },
        shape = RoundedCornerShape(16.dp),
        color = if (picked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (picked) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun LinkExample() {
    val handleStyle = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            buildAnnotatedString {
                append("linkedin.com/in/")
                // Invented on purpose. LinkedIn adds a few characters to a name
                // already taken, so real addresses often end like this, and a
                // reader who sees only "jane-doe" would think theirs is wrong.
                withStyle(handleStyle) { append("jane-doe-5b19a2") }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape
                    )
            )
        }
    }
}
