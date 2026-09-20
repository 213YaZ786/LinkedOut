package com.linkedout.app.feature.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.media.OfflineMedia
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.compose.koinInject
import java.io.File

/**
 * What is actually saved in /Android/data/com.linkedout.app/files/media.
 *
 * This screen exists because Android 11 and later refuse that folder to the
 * Files app and to the picker, so a file manager shows it empty whether it is
 * empty or not. Without a list inside the app there is no way to tell a
 * working download from a broken one, and no way to delete one file.
 */
@Composable
fun SavedMediaScreen(onBack: () -> Unit) {
    val offline: OfflineMedia = koinInject()
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var confirmAll by remember { mutableStateOf(false) }

    fun reload() {
        files = offline.saved()
    }

    LaunchedEffect(Unit) { reload() }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete every saved file?") },
            text = {
                Text(
                    "The posts stay. Their pictures and videos are fetched again the next " +
                        "time you look at them, or saved again on the next refresh."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    offline.deleteAll()
                    confirmAll = false
                    reload()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } }
        )
    }

    val total = files.sumOf { it.length() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "banner") {
            ScreenBanner(
                title = "Saved media",
                subtitle = if (files.isEmpty()) {
                    "Nothing saved yet"
                } else {
                    "${files.size} files, ${Formatter.formatShortFileSize(context, total)}"
                },
                leading = {
                    BannerAction(
                        icon = LinkedOutIcons.ArrowBack,
                        label = "Back",
                        onClick = onBack
                    )
                },
                trailing = {
                    if (files.isNotEmpty()) {
                        BannerAction(
                            icon = LinkedOutIcons.Delete,
                            label = "Delete every saved file",
                            onClick = { confirmAll = true }
                        )
                    }
                }
            )
        }

        if (files.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Turn on automatic downloads in Settings, under Media, and the pictures " +
                        "of the posts you read are kept here for reading offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
            return@LazyColumn
        }

        items(files, key = { it.name }) { file ->
            Zone(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = ZoneGap / 2)
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            Formatter.formatShortFileSize(context, file.length()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        offline.delete(file.name)
                        reload()
                    }) {
                        Icon(
                            LinkedOutIcons.Delete,
                            contentDescription = "Delete this file",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
