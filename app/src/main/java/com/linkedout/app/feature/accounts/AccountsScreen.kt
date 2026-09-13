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
 * accounts you follow, and when the text names a profile you do not follow
 * yet, a card offers to open it or follow it. Typing never follows anyone.
 * Unfollowing happens on the profile, which keeps it away from a stray tap.
 *
 * LinkedIn has no @handle and no guest search by name, so the field takes the
 * vanity name out of linkedin.com/in/<vanity> or a whole address pasted in.
 * A line under the field says so, because nothing else in the app can.
 */
@Composable
fun AccountsScreen(
    onOpenFeed: (String, AccountKind) -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    // Coming back from a profile may have brought new posts or an avatar.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val trimmed = query.trim().removePrefix("@")
    val queryKind = AccountsViewModel.classify(query)
    val asProfile = queryKind as? QueryKind.Profile
    val candidate = asProfile?.handle
    val candidateKind = asProfile?.kind ?: AccountKind.PERSON
    val alreadyFollowed = candidate != null &&
        rows.any { it.handle.equals(candidate, ignoreCase = true) }
    // A pasted address is matched on the vanity it resolves to, otherwise
    // someone you already follow would vanish from the list the moment you
    // paste their link.
    val needle = candidate ?: trimmed
    val visible = if (trimmed.isEmpty()) {
        rows
    } else {
        rows.filter {
            it.handle.contains(needle, ignoreCase = true) ||
                it.name?.contains(trimmed, ignoreCase = true) == true
        }
    }

    fun open(handle: String, kind: AccountKind) {
        focus.clearFocus()
        onOpenFeed(handle, kind)
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
            placeholder = { Text("Name in the address, or paste a link") },
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
                    candidate != null && !alreadyFollowed -> open(candidate, candidateKind)
                    visible.size == 1 -> open(visible.first().handle, visible.first().kind)
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
                        kind = candidateKind,
                        onOpen = { open(candidate, candidateKind) },
                        onFollow = { viewModel.follow(candidate, candidateKind) }
                    )
                }
            } else if (visible.isEmpty()) {
                // Each reason gets its own line. "No match" alone would leave
                // the reader guessing at a field that has no guessable rule.
                explain(queryKind)?.let { message ->
                    item(key = "explain") { Hint(message) }
                }
            }

            items(visible, key = { it.handle }) { row ->
                AccountCard(row = row, onClick = { open(row.handle, row.kind) })
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
                    row.name ?: row.handle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.name != null) {
                    Text(
                        "${row.kind.segment}/${row.handle}",
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
                    handle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "linkedin.com/${kind.segment}/$handle, tap to read",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            FilledTonalButton(onClick = onFollow) { Text("Follow") }
        }
    }
}

/**
 * The line under an empty result. Null when there is nothing useful to say,
 * which is the case for a filter that simply matched none of the accounts.
 */
private fun explain(kind: QueryKind): String? = when (kind) {
    is QueryKind.Profile -> null
    QueryKind.Blank -> null
    QueryKind.PostLink -> "That is a link to one post. Tap it outside the app to open it here."
    QueryKind.ShortLink -> "A lnkd.in link hides where it goes. Open it once in a browser, " +
        "then paste the address it lands on."
    QueryKind.Unusable -> "No match, and that is not a LinkedIn address. There is no " +
        "searchable handle, so use the part after /in/ or /company/ in the address, or paste " +
        "the address itself. A person's or a company's name will not work."
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
            "LinkedIn has no @handle. Open a profile or a company page in a browser and " +
                "paste its address above, or type just the part after /in/. Reading it once " +
                "is enough to follow it and build your timeline. Who you follow stays on this " +
                "phone and is never sent anywhere but to linkedin.com itself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
