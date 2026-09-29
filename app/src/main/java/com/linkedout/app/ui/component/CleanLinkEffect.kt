package com.linkedout.app.ui.component

import com.linkedout.app.core.link.LinkCleaner
import com.linkedout.app.core.link.RedirectResolver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import org.koin.compose.koinInject

/**
 * Keeps a search field's link clean: a detour copied from a search engine or
 * another site, pasted with the button or with the keyboard, is replaced by
 * the address it stands for, so the reader never has to open it first. That
 * matters where the site itself would stop a logged out visitor.
 *
 * Offline for every detour that carries its address; Google's links that hide
 * it are asked of Google once, see RedirectResolver. [onClean] gets the clean
 * address, [onUnreadable] is called when Google did not say where one leads.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun CleanLinkEffect(text: String, onClean: (String) -> Unit, onUnreadable: () -> Unit) {
    val resolver: RedirectResolver = koinInject()
    val clean by rememberUpdatedState(onClean)
    val unreadable by rememberUpdatedState(onUnreadable)
    LaunchedEffect(text) {
        val raw = text.trim()
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) return@LaunchedEffect
        val offline = LinkCleaner.clean(raw)
        when {
            LinkCleaner.needsResolving(offline) -> resolver.resolve(offline)?.let(clean) ?: unreadable()
            offline != raw -> clean(offline)
        }
    }
}
