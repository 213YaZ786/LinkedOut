package com.linkedout.app.feature.accounts

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.model.FollowedAccount
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Folders, and what is in them.
 *
 * One folder holds an account, never two, so filing is moving rather than
 * adding, and a tick means "this is where it lives". Opening a folder lists
 * every account you follow: tap one to move it in, tap it again to send it
 * back to the main folder.
 *
 * The main folder is where an account lands when it has been put nowhere. It
 * cannot be deleted or renamed for that reason.
 */
@Composable
fun FoldersScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    if (creating) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            onConfirm = { name ->
                creating = false
                open = name.trim().takeIf { it.isNotEmpty() }
            },
            onDismiss = { creating = false }
        )
    }

    renaming?.let { from ->
        NameDialog(
            title = "Rename $from",
            initial = from,
            confirm = "Rename",
            onConfirm = { name ->
                viewModel.renameFolder(from, name)
                if (open == from) open = name.trim()
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { name ->
        val count = rows.count { it.folder == name }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = {
                Text(
                    if (count == 0) {
                        "Nothing is filed here."
                    } else {
                        "Its ${if (count == 1) "account goes" else "$count accounts go"} back " +
                            "to ${FollowedAccount.MAIN}. Nothing is unfollowed."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.done()
                    viewModel.deleteFolder(name)
                    if (open == name) open = null
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    // A folder being filled but still empty exists only here, until the first
    // account is filed in it. Nothing is written until then, which is why a
    // new folder opens straight away.
    val names = remember(folders, open) {
        (folders + listOfNotNull(open)).distinct()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "banner") {
            ScreenBanner(
                title = "Folders",
                subtitle = if (names.size == 1) "1 folder" else "${names.size} folders",
                leading = {
                    BannerAction(
                        icon = LinkedOutIcons.ArrowBack,
                        label = "Back",
                        onClick = onBack
                    )
                },
                trailing = {
                    BannerAction(
                        icon = LinkedOutIcons.Add,
                        label = "New folder",
                        onClick = { creating = true }
                    )
                }
            )
        }

        items(names, key = { it }) { name ->
            val count = rows.count { it.folder == name }
            Zone(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = ZoneGap)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when (count) {
                                    0 -> "Empty"
                                    1 -> "1 account"
                                    else -> "$count accounts"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (name != FollowedAccount.MAIN) {
                            TextButton(onClick = { renaming = name }) { Text("Rename") }
                            IconButton(onClick = { deleting = name }) {
                                Icon(
                                    LinkedOutIcons.Delete,
                                    contentDescription = "Delete this folder",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        TextButton(onClick = { open = if (open == name) null else name }) {
                            Text(if (open == name) "Close" else "Open")
                        }
                    }

                    AnimatedVisibility(visible = open == name) {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            if (rows.isEmpty()) {
                                Text(
                                    "Follow an account first, then file it here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                            rows.forEach { row ->
                                val here = row.folder == name
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .then(Modifier),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            // Filing is moving, so it answers
                                            // like something taken.
                                            haptics.firm()
                                            viewModel.setFolder(
                                                row.handle,
                                                if (here) FollowedAccount.MAIN else name
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Avatar(
                                            url = row.avatarUrl,
                                            name = row.name ?: row.handle,
                                            size = 28.dp
                                        )
                                        Text(
                                            row.name ?: row.handle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 10.dp)
                                        )
                                        if (here) {
                                            Icon(
                                                LinkedOutIcons.Check,
                                                contentDescription = "Filed here",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                row.folder,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
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
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Folder name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
