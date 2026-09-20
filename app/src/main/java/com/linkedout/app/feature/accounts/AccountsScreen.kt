package com.linkedout.app.feature.accounts

import android.content.ClipboardManager
import com.linkedout.app.core.link.PastedText
import com.linkedout.app.core.model.AccountKind
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.FolderDialog
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
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
 *
 * Since the address has to come from somewhere else, the field carries a paste
 * button: copy the page in the browser, tap it once, the profile opens. The
 * button is always there and reads the clipboard only when tapped. Reading it
 * to decide whether to show the button would be a read as well, and Android
 * raises its own notice on every one of them.
 */
@Composable
fun AccountsScreen(
    onOpenFeed: (String, AccountKind) -> Unit,
    onOpenFolders: () -> Unit,
    viewModel: AccountsViewModel = koinViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val folders by viewModel.folders.collectAsState()
    var filing by remember { mutableStateOf<AccountRow?>(null) }

    filing?.let { row ->
        FolderDialog(
            title = row.name ?: row.handle,
            folders = folders,
            selected = row.folder,
            everything = null,
            onSelect = { name ->
                viewModel.setFolder(row.handle, name.orEmpty())
                filing = null
            },
            onDismiss = { filing = null }
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current

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

    /**
     * One tap: read the clipboard, put what it holds in the field, and open
     * the account it names. The clipboard often holds a sentence with the
     * address inside it rather than the address alone, which is why the text
     * goes through PastedText first. Anything that is not an account is left
     * in the field, where the line underneath already explains why.
     */
    fun paste() {
        val clip = context.getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        val text = PastedText.query(clip)
        if (text.isEmpty()) {
            notice = "Nothing to paste. Open a profile in your browser and copy its address."
            return
        }
        notice = null
        query = text
        val found = AccountsViewModel.classify(text) as? QueryKind.Profile
        if (found != null) {
            open(found.handle, found.kind)
        } else {
            focus.clearFocus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenBanner(
            title = "Accounts",
            subtitle = if (rows.isEmpty()) null else "${rows.size} followed",
            trailing = {
                BannerAction(
                    icon = LinkedOutIcons.Folder,
                    label = "Folders",
                    onClick = onOpenFolders
                )
            }
        )

        TextField(
            value = query,
            onValueChange = {
                query = it
                notice = null
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Paste a full profile address") },
            leadingIcon = { Icon(LinkedOutIcons.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            notice = null
                        }) {
                            Icon(LinkedOutIcons.Close, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = { paste() }) {
                        Icon(LinkedOutIcons.Paste, contentDescription = "Paste an address")
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

        // Said under the field and not only in the empty screen, because the
        // rule is the opposite of every other social app: there is no handle
        // to guess here, "@chu-nantes" is not an address and never resolves.
        if (trimmed.isEmpty()) {
            Text(
                "LinkedIn has no @handle. Paste the whole address, " +
                    "linkedin.com/in/name or linkedin.com/company/name.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalDockPadding.current),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            notice?.let { message ->
                item(key = "notice") { Hint(message) }
            }

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
                AccountCard(
                    row = row,
                    onClick = { open(row.handle, row.kind) },
                    onFile = { filing = row }
                )
            }

            if (rows.isEmpty() && trimmed.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }
        }
    }
}

@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit, onFile: () -> Unit) {
    Zone(
        onClick = onClick,
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The folder is a control, not a label. Tapping the card opens
                // the account, so filing it needs a target of its own.
                TextButton(onClick = onFile, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        row.folder,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(
    handle: String,
    kind: AccountKind,
    onOpen: () -> Unit,
    onFollow: () -> Unit
) {
    Zone(
        onClick = onOpen,
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
