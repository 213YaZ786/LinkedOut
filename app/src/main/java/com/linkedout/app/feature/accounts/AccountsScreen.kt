package com.linkedout.app.feature.accounts

import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.core.link.LinkCleaner
import com.linkedout.app.ui.component.CleanLinkEffect
import com.linkedout.app.ui.component.rememberHaptics
import com.linkedout.app.ui.component.statusBarTop
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import com.linkedout.app.ui.component.EmptyZone
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.theme.zone
import androidx.compose.foundation.layout.Box
import com.linkedout.app.navigation.LocalReadableInset
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.linkedout.app.ui.component.DropdownMenuGroup
import com.linkedout.app.ui.component.DropdownMenuItem
import com.linkedout.app.ui.component.GlassMenu
import com.linkedout.app.ui.component.MenuPill
import com.linkedout.app.ui.component.SelectableDropdownMenuItem
import com.linkedout.app.ui.component.FolderDialog
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.HoldAction
import com.linkedout.app.ui.component.HoldMenu
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.ZoneAlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
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
    val haptics = rememberHaptics()

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
    // Unfollowing asks first, as the profile's button does, a held row must not drop an account by a stray tap:
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#L113-L127
    var confirmUnfollow by remember { mutableStateOf<AccountRow?>(null) }
    confirmUnfollow?.let { row ->
        ZoneAlertDialog(
            onDismissRequest = { confirmUnfollow = null },
            title = { Text("Unfollow ${row.name ?: row.handle}?") },
            confirmButton = {
                QuietButton(onClick = {
                    confirmUnfollow = null
                    haptics.done()
                    viewModel.unfollow(row.handle)
                }) { Text("Unfollow") }
            },
            dismissButton = { QuietButton(onClick = { confirmUnfollow = null }) { Text("Cancel") } }
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    // The folder chip chosen under the field, null for All. A folder deleted since is All again.
    var chosenFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = chosenFolder?.takeIf { it in folders }
    var foldersOpen by remember { mutableStateOf(false) }
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
    val inFolder = if (folder == null) rows else rows.filter { it.folder == folder }
    val visible = if (trimmed.isEmpty()) {
        inFolder
    } else {
        inFolder.filter {
            it.handle.contains(needle, ignoreCase = true) ||
                it.name?.contains(trimmed, ignoreCase = true) == true
        }
    }

    fun open(handle: String, kind: AccountKind) {
        focus.clearFocus()
        onOpenFeed(handle, kind)
    }

    // A detour link in the field, from a search engine or another site,
    // becomes the address it stands for; a pasted one then opens like any
    // other paste.
    var openWhenClean by remember { mutableStateOf(false) }
    CleanLinkEffect(
        text = query,
        onClean = { clean ->
            query = clean
            if (openWhenClean) {
                openWhenClean = false
                (AccountsViewModel.classify(clean) as? QueryKind.Profile)?.let { open(it.handle, it.kind) }
            }
        },
        onUnreadable = {
            openWhenClean = false
            haptics.reject()
            notice = "Google did not say where this link leads. Open it and copy the page address."
        }
    )

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
        val text = PastedText.query(clip) { "linkedin.com/" in it || "lnkd.in/" in it }
        if (text.isEmpty()) {
            haptics.reject()
            notice = "Nothing to paste. Open a profile in your browser and copy its address."
            return
        }
        notice = null
        query = text
        if (LinkCleaner.needsResolving(text)) {
            // Opened once Google has said where it leads, see CleanLinkEffect.
            openWhenClean = true
            focus.clearFocus()
            return
        }
        val found = AccountsViewModel.classify(text) as? QueryKind.Profile
        if (found != null) {
            open(found.handle, found.kind)
        } else {
            focus.clearFocus()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp + LocalReadableInset.current,
                top = statusBarTop(),
                end = 16.dp + LocalReadableInset.current,
                bottom = 16.dp + LocalDockPadding.current
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The banner, the field and the line under it are the first row of
            // the list, so the whole screen scrolls, under the status bar too.
            // The row undoes the list's side padding and keeps its own margins.
            item(key = "header") {
                Column(Modifier.fullBleed(16.dp + LocalReadableInset.current).padding(bottom = 8.dp)) {
                Box(Modifier.padding(horizontal = LocalReadableInset.current)) {
                // The folder choice in the banner's middle as Home's feed pill: one pill, a menu under it with the folders,
                // then Manage folders last (Gmail's Manage labels), the shown folder checked.
                // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#TimelineScreen
                // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
                // not ported: TimelineScreen (the screen itself, only its feed pill and menu are rebuilt here, without Liked and Archived)
                // not ported: DropdownMenuGroup (the app's ui/component/GlassMenu.kt is its port, used as it is)
                ScreenBanner(
                    // Nobody followed: no pill, no count, the row stays empty above the field (requests.md point 12).
                    centre = if (rows.isNotEmpty()) {
                        {
                            // The pill is an item of the list that records the backdrop: MenuPill draws it with none and
                            // the menu lives in a window of its own (TimelineScreen's feedPill, same pitfall).
                            // The wrapper is the screen's heading for TalkBack, the pill inside stays its own button.
                            Box(Modifier.semantics { heading(); contentDescription = "Accounts" }) {
                                Box {
                                    MenuPill(
                                        icon = LinkedOutIcons.Folder,
                                        label = folder ?: "All",
                                        expanded = foldersOpen,
                                        trailingIcon = LinkedOutIcons.ExpandMore,
                                        onClick = { foldersOpen = true }
                                    )
                                    GlassMenu(expanded = foldersOpen, onDismissRequest = { foldersOpen = false }) {
                                        DropdownMenuGroup(index = 0, count = 2) {
                                            SelectableDropdownMenuItem(
                                                selected = folder == null,
                                                onClick = { chosenFolder = null; foldersOpen = false },
                                                text = "All"
                                            )
                                            folders.forEach { name ->
                                                SelectableDropdownMenuItem(
                                                    selected = folder == name,
                                                    onClick = { chosenFolder = name; foldersOpen = false },
                                                    text = name
                                                )
                                            }
                                        }
                                        DropdownMenuGroup(index = 1, count = 2) {
                                            DropdownMenuItem(
                                                text = "Manage folders",
                                                onClick = { foldersOpen = false; onOpenFolders() },
                                                leadingIcon = {
                                                    Icon(LinkedOutIcons.Tune, contentDescription = null, modifier = Modifier.size(24.dp))
                                                },
                                                trailingIcon = {
                                                    Icon(
                                                        LinkedOutIcons.ChevronRight,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Nothing to see, but TalkBack still finds the screen's name.
                        { Box(Modifier.size(1.dp).semantics { heading(); contentDescription = "Accounts" }) }
                    },
                    subtitle = if (rows.isEmpty()) null else "${rows.size} followed"
                )
                }

                GlassSearchField(
                    value = query,
                    onValueChange = {
                        query = it
                        notice = null
                    },
                    placeholder = "Profile address",
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
                            // The paste action of MTGA's field, always there and the clipboard read only on a tap
                            // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/AccountsScreen.kt#L209-L226),
                            // as a word instead of its icon: a transparent tap target in the field's own glass,
                            // never a second pane.
                            Surface(
                                onClick = { paste() },
                                shape = ButtonDefaults.textShape,
                                color = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = ButtonDefaults.MinHeight)
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Paste", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        when {
                            candidate != null && !alreadyFollowed -> open(candidate, candidateKind)
                            visible.size == 1 -> open(visible.first().handle, visible.first().kind)
                            else -> focus.clearFocus()
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp + LocalReadableInset.current)
                )
                }
            }
            notice?.let { message ->
                item(key = "notice") { Hint(message) }
            }

            if (candidate != null && !alreadyFollowed) {
                item(key = "candidate") {
                    CandidateCard(
                        handle = candidate,
                        kind = candidateKind,
                        onOpen = { open(candidate, candidateKind) },
                        onFollow = { haptics.done(); viewModel.follow(candidate, candidateKind) }
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
                    onFile = { filing = row },
                    onUnfollow = { confirmUnfollow = row }
                )
            }

            if (rows.isEmpty() && trimmed.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.fillParentMaxHeight(0.7f)) }
            }
        }
    }
}

/**
 * One followed account: the avatar, the name with its headline under it, and at the end the age of its last post
 * with the unread count under it. The folder label is gone, the chips under the field stand for it. A tap opens
 * the account, a long press the suite's HoldMenu (Open, File, Unfollow), the zone drawn twice and the menu opened
 * as the post card does, no swipe because the tabs swipe sideways. This card as it was, then the post card's
 * zone and menu:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/accounts/AccountsScreen.kt#AccountCard
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/PostCard.kt#L146-L159
 */
@Composable
private fun AccountCard(row: AccountRow, onClick: () -> Unit, onFile: () -> Unit, onUnfollow: () -> Unit) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    // The zone's place in its window, which HoldMenu lifts the copy from.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val zone: @Composable (Modifier, Modifier) -> Unit = { outer, clicks ->
        Zone(
            color = MaterialTheme.colorScheme.zone,
            modifier = outer.fillMaxWidth()
        ) {
            Row(
                modifier = clicks.padding(horizontal = 16.dp, vertical = 12.dp),
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
                    // The headline when a page gave one, else where the account lives on LinkedIn.
                    val line = row.headline ?: row.name?.let { "${row.kind.segment}/${row.handle}" }
                    if (line != null) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        row.lastPostMillis?.let(::relativeTime) ?: "Not read yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (row.unread > 0) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                row.unread.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
    Box {
        zone(
            Modifier
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer { alpha = if (menuOpen) 0f else 1f },
            Modifier.combinedClickable(
                onClick = { haptics.tick(); onClick() },
                onLongClick = { haptics.firm(); menuOpen = true }
            )
        )
        if (menuOpen) {
            HoldMenu(
                bounds = bounds,
                actions = listOf(
                    HoldAction(LinkedOutIcons.Person, "Open", run = onClick),
                    HoldAction(LinkedOutIcons.Folder, "File", run = onFile),
                    HoldAction(LinkedOutIcons.Delete, "Unfollow", danger = true, run = onUnfollow)
                ),
                onDismiss = { menuOpen = false }
            ) { zone(Modifier, Modifier) }
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
            BoldButton(onClick = onFollow, filled = true) { Text("Follow") }
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

/**
 * The round mark and one line, nothing else: the field above already says what to paste. MTGA's EmptyState, its
 * EmptyZone with the title and the message cut to the one line, the mark on a circle as EmptyZone holds its icon:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/accounts/AccountsScreen.kt#EmptyState
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    EmptyZone(
        // The line is the message, grey as in the picture, the accent title stays empty.
        title = "",
        message = "Nobody followed yet",
        icon = LinkedOutIcons.Person,
        iconShape = CircleShape,
        modifier = modifier
    )
}

/**
 * Undoes the list's side padding for a row that brings its own margins, so the
 * header keeps exactly the layout it had above the list.
 */
private fun Modifier.fullBleed(side: Dp) = layout { measurable, constraints ->
    val extra = side.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra)
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/**
 * Accounts' top row (requests.md point 12): Home's top row (TimelineScreen's HomeBanner, itself ScreenBanner with the
 * centre open to a composable), one header row as Bluesky's HomeHeaderLayoutMobile (a slot, the centre taking the rest,
 * a slot). The centre is the folder pill with the subtitle beside it, 12 dp apart, the pair centred between the two
 * action slots (both keep their room, so the pair is centred on the screen). A pair too long for the line drops the
 * subtitle under the centre instead of cutting it.
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * not ported: BannerAction (the slots stay empty here), the title's two lines with TextAutoSize (the centre is a pill),
 * the logo and the hide on scroll (Bluesky's header only)
 */
@Composable
private fun ScreenBanner(
    centre: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both action slots keep their room, so the pair stays centred.
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        Layout(
            content = {
                centre()
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) { measurables, constraints ->
            val gap = 12.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val centreItem = measurables[0].measure(loose)
            val subtitleText = measurables.getOrNull(1)?.measure(loose)
            if (subtitleText == null || centreItem.width + gap + subtitleText.width <= constraints.maxWidth) {
                // One line: the pill and the count centred on each other (a pill has no text baseline to share), the pair centred.
                val height = maxOf(centreItem.height, subtitleText?.height ?: 0)
                val width = centreItem.width + (subtitleText?.let { gap + it.width } ?: 0)
                layout(constraints.maxWidth, height) {
                    val start = (constraints.maxWidth - width) / 2
                    centreItem.place(start, (height - centreItem.height) / 2)
                    subtitleText?.place(start + centreItem.width + gap, (height - subtitleText.height) / 2)
                }
            } else {
                // Too narrow: the subtitle under the centre, each centred.
                val under = 4.dp.roundToPx()
                layout(constraints.maxWidth, centreItem.height + under + subtitleText.height) {
                    centreItem.place((constraints.maxWidth - centreItem.width) / 2, 0)
                    subtitleText.place((constraints.maxWidth - subtitleText.width) / 2, centreItem.height + under)
                }
            }
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/** Space the banner leaves above and below itself, so it never touches what follows. */
private val BannerGap = 8.dp

/** Both slots of the banner, so an empty one still holds the pair centred. */
private val BannerActionSize = 44.dp
