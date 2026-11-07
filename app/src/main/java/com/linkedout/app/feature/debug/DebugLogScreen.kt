package com.linkedout.app.feature.debug

import androidx.compose.ui.graphics.Color
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import androidx.compose.foundation.shape.RoundedCornerShape
import com.linkedout.app.ui.component.ZoneSurface
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.navigation.LocalReadableInset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.debug.LogExporter
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.WindowInsets

/**
 * Every request and its result, exportable as a text file.
 *
 * The point is that a bug report can carry evidence rather than a description.
 * Nothing here leaves the device unless you export it and send it yourself.
 */
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val log: RequestLog = koinInject()
    val exporter: LogExporter = koinInject()
    val entries by log.entries.collectAsState()
    val lastBody by log.lastBody.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val stamp = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // The NavHost's own Scaffold already stands clear of the status and
        // navigation bars. A nested Scaffold applies them a second time, which
        // is where the empty band above and below the content came from. The
        // list below owns them once, in its content padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        // The whole page is one list, the banner of the profile page its first row
        // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#L173-L206
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                top = statusBarTop(),
                bottom = 12.dp + navigationBarBottom()
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "banner") {
                ScreenBanner(
                    title = "Activity log",
                    leading = {
                        BannerAction(
                            icon = LinkedOutIcons.ArrowBack,
                            label = "Back",
                            onClick = onBack
                        )
                    },
                    trailing = {
                        BannerAction(
                            icon = LinkedOutIcons.Delete,
                            label = "Clear",
                            onClick = log::clear
                        )
                    }
                )
            }

            item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BoldButton(onClick = {
                    clipboard.setText(AnnotatedString(log.render()))
                    haptics.done()
                    scope.launch { snackbar.showSnackbar("Log copied") }
                }) { Text("Copy all") }

                BoldButton(onClick = {
                    scope.launch {
                        val name = "linkedout-log-${System.currentTimeMillis()}.txt"
                        exporter.exportText(name, log.render())
                            .onSuccess { snackbar.showSnackbar("Saved to $it") }
                            .onFailure { snackbar.showSnackbar("Could not save the file") }
                    }
                }) { Text("Save to Downloads") }

                // Only offered once a page has actually arrived. Saving the
                // page LinkedIn sent is the only way to tell a reader looking
                // in the wrong place from markup that never arrived, since the
                // app runs none of the page's script and a browser runs all of
                // it.
                lastBody?.let { body ->
                    BoldButton(onClick = {
                        scope.launch {
                            val name = "linkedout-page-${System.currentTimeMillis()}.html"
                            exporter.exportText(name, body.text)
                                .onSuccess { snackbar.showSnackbar("Saved to $it") }
                                .onFailure { snackbar.showSnackbar("Could not save the file") }
                        }
                    }) { Text("Save page") }
                }
            }
            }

            lastBody?.let { body ->
                item(key = "lastpage") {
                Text(
                    "Last page received: ${body.url}, ${body.text.length} characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                }
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                Text(
                    "Nothing recorded yet. Open an account or scroll for older posts, then " +
                        "come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                )
                }
            } else {
                    items(entries.reversed()) { entry ->
                        ZoneSurface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${stamp.format(Date(entry.atMillis))}  ${entry.kind}  ${entry.outcome}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    entry.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val meta = buildList {
                                    entry.httpStatus?.let { add("http $it") }
                                    entry.durationMillis?.let { add("${it}ms") }
                                    entry.bodyBytes?.let { add("$it bytes") }
                                }
                                if (meta.isNotEmpty()) {
                                    // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
                                    Text(
                                        meta.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                entry.detail?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}
