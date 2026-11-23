package com.linkedout.app.feature.accounts

import com.linkedout.app.ui.component.ZoneAlertDialog
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.navigation.LocalReadableInset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.DialogNameField
import com.linkedout.app.ui.component.rememberDialogNameState
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
import com.linkedout.app.ui.component.BarTitle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.ui.component.MovableAction
import com.linkedout.app.ui.component.TopActionSize
import org.koin.compose.koinInject
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Folders, and what is in them.
 *
 * A folder exists as soon as it is created, empty, and Home can be switched to
 * it straight away. It used to exist only as the name its accounts carried, so
 * a new folder was invisible everywhere else until something was filed in it.
 *
 * One folder holds an account, never two, so filing is moving rather than
 * adding, and a tick means "this is where it lives". Opening a folder lists
 * every account you follow: tap one to move it in, tap it again to send it
 * back to the main folder.
 *
 * The main folder is where an account lands when it has been put nowhere. It
 * cannot be deleted or renamed for that reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun FoldersScreen(
    // Kept for the caller: the system back gesture does the going back now, the arrow is gone.
    onBack: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel(),
    store: SettingsStore = koinInject()
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
                // The folder is written now, empty, so Home can offer it
                // before anything is filed in it.
                open = viewModel.createFolder(name)
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
                // The store answers with the name the folder really has now,
                // which after a merge is the other folder's spelling.
                val renamed = viewModel.renameFolder(from, name)
                if (open == from && renamed != null) open = renamed
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { name ->
        val count = rows.count { it.folder == name }
        ZoneAlertDialog(
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
                BoldButton(filled = true, onClick = {
                    haptics.done()
                    viewModel.deleteFolder(name)
                    if (open == name) open = null
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { QuietButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    // Ported from MTGA's FoldersScreen: no bar and no arrow, the title first in the list, a movable + on the page
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/FoldersScreen.kt#FoldersScreen).
    // The system gesture goes back. The page's ground is painted under the whole app, see MainActivity.
    val settings by store.settings.collectAsState()
    val statusTop = statusBarTop()
    val navigationBottom = navigationBarBottom()
    Box(Modifier.fillMaxSize()) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Room under the last folder for the button: its 48 dp and the 16 dp around it.
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current,
            top = 0.dp,
            bottom = navigationBottom + 16.dp + TopActionSize + 16.dp
        )
    ) {
        // The title on the page, no zone, first in the list: it scrolls away with it.
        item(key = "title") {
            Box(
                Modifier.fillMaxWidth().padding(top = statusTop + 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { BarTitle("Folders") }
            }
        }
        items(folders, key = { it }) { name ->
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
                            BoldButton(onClick = { renaming = name }) { Text("Rename") }
                            BoldIconButton(onClick = { deleting = name }) {
                                Icon(
                                    LinkedOutIcons.Delete,
                                    contentDescription = "Delete this folder",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        BoldButton(onClick = { open = if (open == name) null else name }) {
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
                                    // A row inside the folder's zone: a transparent tap target, never a
                                    // second glass zone (Modules, "Liquid glass, as built").
                                    Surface(
                                        onClick = {
                                            // Filing is moving, so it answers
                                            // like something taken.
                                            haptics.firm()
                                            viewModel.setFolder(
                                                row.handle,
                                                if (here) FollowedAccount.MAIN else name
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = ButtonDefaults.textShape,
                                        color = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = ButtonDefaults.MinHeight)
                                                .padding(ButtonDefaults.TextButtonContentPadding),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
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
    MovableAction(
        icon = LinkedOutIcons.Add,
        label = "New folder",
        snapFraction = settings.foldersButtonSnap,
        onSnapFraction = { fraction -> store.update { it.copy(foldersButtonSnap = fraction) } },
        onClick = { creating = true }
    )
    }
}

// Ported from MTGA's NameDialog, which takes Android Files' New folder field:
// https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/FoldersScreen.kt#NameDialog
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDialogNameState(initial, selectAll = initial.isNotEmpty())
    val name = state.text.toString()
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            DialogNameField(state, "Folder name", onDone = { if (name.isNotBlank()) onConfirm(name) })
        },
        confirmButton = {
            BoldButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank(), filled = true) {
                Text(confirm)
            }
        },
        dismissButton = { QuietButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
