package com.linkedout.app.feature.accounts

import com.linkedout.app.ui.component.LocalDockPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * Accounts and search merged. One pill search bar on top: it filters the
 * accounts you follow, and when the text is a handle you do not follow yet, a
 * card offers to open that profile or follow it. Typing never follows anyone.
 * Unfollowing happens on the profile, which keeps it away from a stray tap.
 */
@Composable
fun AccountsScreen(
    onOpenFeed: (String) -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    // Coming back from a profile may have brought new posts or an avatar.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val trimmed = query.trim().removePrefix("@")
    val candidate = AccountsViewModel.asHandle(query)
    val alreadyFollowed = candidate != null &&
        rows.any { it.handle.equals(candidate, ignoreCase = true) }
    val visible = if (trimmed.isEmpty()) {
        rows
    } else {
        rows.filter {
            it.handle.contains(trimmed, ignoreCase = true) ||
                it.name?.contains(trimmed, ignoreCase = true) == true
        }
    }

    fun open(handle: String) {
        focus.clearFocus()
        onOpenFeed(handle)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("Accounts", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (rows.isNotEmpty()) {
                Text(
                    "${rows.size} followed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Search or open a handle") },
            leadingIcon = { Icon(LinkedOutIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(LinkedOutIcons.Close, contentDescription = "Clear")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                when {
                    candidate != null && !alreadyFollowed -> open(candidate)
                    visible.size == 1 -> open(visible.first().handle)
                    else -> focus.clearFocus()
                }
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalDockPadding.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (candidate != null && !alreadyFollowed) {
                item(key = "candidate") {
                    CandidateCard(
                        handle = candidate,
                        onOpen = { open(candidate) },
                        onFollow = { viewModel.follow(candidate) }
                    )
                }
            } else if (trimmed.isNotEmpty() && candidate == null && visible.isEmpty()) {
                item(key = "invalid") {
                    Hint("No match. A handle is letters, digits and underscores, 15 at most.")
                }
            }

            items(visible, key = { it.handle }) { row ->
                AccountCard(row = row, onClick = { open(row.handle) })
            }

            if (rows.isEmpty() && trimmed.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
        }
    }
}

@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Avatar(url = row.avatarUrl, name = row.name ?: row.handle, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    row.name ?: "@${row.handle}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.name != null) {
                    Text(
                        "@${row.handle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CandidateCard(handle: String, onOpen: () -> Unit, onFollow: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Avatar(url = null, name = handle, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "@$handle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Tap to read the profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            FilledTonalButton(onClick = onFollow) { Text("Follow") }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            LinkedOutIcons.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text("No accounts yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Type a handle above to read a profile, then follow it to build your timeline. " +
                "Handles stay on this device and are never sent anywhere except to the server " +
                "that serves the feed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
