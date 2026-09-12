package com.linkedout.app.feature.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.linkedout.app.ui.icon.LinkedOutIcons
import kotlinx.coroutines.launch

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val intro: String,
    val points: List<String>,
    /** Shows "x.com/nytimes" with the handle picked out. */
    val showLinkExample: Boolean = false
)

private val PAGES = listOf(
    WelcomePage(
        icon = LinkedOutIcons.Home,
        title = "Welcome to LinkedOut",
        intro = "Read public posts from X with no account, no tracking and no ads.",
        points = listOf(
            "You choose the accounts to follow. The list stays on this device.",
            "Home gathers their newest posts in one timeline."
        )
    ),
    WelcomePage(
        icon = LinkedOutIcons.Person,
        title = "Find a handle",
        intro = "Every X account has a handle, the name written after the @. For example @nytimes.",
        points = listOf(
            "On a profile, it sits right under the display name.",
            "In a profile link, it comes right after x.com/ as below.",
            "It is made of letters, digits and underscores, 15 at most."
        ),
        showLinkExample = true
    ),
    WelcomePage(
        icon = LinkedOutIcons.Search,
        title = "Follow an account",
        intro = "Three ways to add someone.",
        points = listOf(
            "In Accounts, type the handle or paste the profile link in the search bar, then tap Follow.",
            "In the X app or a browser, share a profile to LinkedOut. It opens here, then tap Follow.",
            "Coming from Fritter or Squawker? Import your list in Settings, under Data."
        )
    )
)

/**
 * A short guide shown on first launch, and again from Settings. Three pages:
 * what LinkedOut is, what a handle is and where to find one, and how to follow.
 *
 * [onFinish] receives true when the reader asks to go to Accounts.
 */
@Composable
fun WelcomeScreen(onFinish: (openAccounts: Boolean) -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onFinish(false) }) { Text(if (last) "Close" else "Skip") }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { index -> PageContent(PAGES[index]) }

        Dots(count = PAGES.size, current = pager.currentPage)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text("Back")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (last) onFinish(true) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            ) { Text(if (last) "Go to Accounts" else "Next") }
        }
    }
}

@Composable
private fun PageContent(page: WelcomePage) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            page.intro,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (page.showLinkExample) LinkExample()
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            page.points.forEach { Point(it) }
        }
    }
}

@Composable
private fun LinkExample() {
    val handleStyle = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            buildAnnotatedString {
                append("https://x.com/")
                withStyle(handleStyle) { append("nytimes") }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        CircleShape
                    )
            )
        }
    }
}
