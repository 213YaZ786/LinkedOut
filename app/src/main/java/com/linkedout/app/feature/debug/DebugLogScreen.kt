package com.linkedout.app.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

/**
 * Every request and its result, exportable as a text file.
 *
 * The point is that a bug report can carry evidence rather than a description.
 * Nothing here leaves the device unless you export it and send it yourself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val log: RequestLog = koinInject()
    val exporter: LogExporter = koinInject()
    val entries by log.entries.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val stamp = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Activity log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LinkedOutIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = log::clear) {
                        Icon(LinkedOutIcons.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(log.render()))
                    scope.launch { snackbar.showSnackbar("Log copied") }
                }) { Text("Copy all") }

                TextButton(onClick = {
                    scope.launch {
                        val name = "linkedout-log-${System.currentTimeMillis()}.txt"
                        exporter.exportText(name, log.render())
                            .onSuccess { snackbar.showSnackbar("Saved to $it") }
                            .onFailure { snackbar.showSnackbar("Could not save the file") }
                    }
                }) { Text("Save to Downloads") }
            }

            if (entries.isEmpty()) {
                Text(
                    "Nothing recorded yet. Open an account or scroll for older posts, then " +
                        "come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries.reversed()) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
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
                                    Text(
                                        meta.joinToString("  ·  "),
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
}
