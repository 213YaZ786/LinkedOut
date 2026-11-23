package com.linkedout.app.feature.jobs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.LocalContentColor
import com.linkedout.app.ui.component.LoadingMark
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.data.marks.SavedJobs
import org.koin.compose.koinInject
import com.linkedout.app.data.linkedin.JobsParser
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.feature.BarAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.plus
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * One offer: who, where, what kind of contract, and the whole description,
 * read from its guest page. Applying happens on LinkedIn, which is where the
 * button leads, outside the app.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenCompany: (handle: String, kind: AccountKind) -> Unit,
    viewModel: JobDetailViewModel = koinViewModel(),
    savedJobs: SavedJobs = koinInject()
) {
    val state by viewModel.state.collectAsState()
    val saved by savedJobs.saved.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.load(id) }
    val offer = state.detail
    val kept = offer != null && saved.isSaved(offer.id)

    // Ported from MTGA's DiagnosticsScreen: the bar folds away going down and returns going up, the list full screen
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/diagnostics/DiagnosticsScreen.kt#DiagnosticsScreen).
    // not ported: DiagnosticsScreen's other pieces (the instance cards, Add dialog, list update)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // The list owns the navigation bar in its content padding, the bar clears the status bar itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Offer") },
                navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onBack) },
                // Save on the trailing side, as Refresh on a profile and Share on a post: the bookmark in the
                // accent while the offer is kept.
                actions = {
                    // The action fades and scales in once the offer exists, as ScrollUpButton does, and its tint goes
                    // between the bar's colour and the accent instead of switching.
                    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
                    // not ported: animatedVisibilityWithBooleanVisibleParamNoReceiver (the sample's function: its clickable Box,
                    // the visible toggle and the corner radius transition are the sample's own demo)
                    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt#SimpleAnimatedContentSample
                    // not ported: simpleAnimatedContentSample, foo, bar, baz (the Save/Saved label has no text to cross:
                    // BarAction's label is the icon's contentDescription, never drawn)
                    val visible = offer != null
                    val tint by animateColorAsState(
                        if (kept) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        label = "savedTint"
                    )
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.8f)
                    ) {
                        BarAction(
                            icon = LinkedOutIcons.Bookmark,
                            label = if (kept) "Saved" else "Save",
                            onClick = { if (offer != null) savedJobs.toggle(offer) },
                            tint = tint
                        )
                    }
                }
            )
        }
    ) { padding ->
    // The loading mark, the failure and the offer cross, as the draft's "state" case.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/CrossfadeSample.kt#CrossfadeSample
    // not ported: crossfadeSample (the sample's function; its Text pages are the screen's own states here)
    // not ported: "A" and "B" (the sample's own pages)
    val screen = when {
        state.loading -> "Loading"
        state.detail == null -> "Failed"
        else -> "Offer"
    }
    Crossfade(targetState = screen, animationSpec = tween(200)) { contentState ->
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current,
            bottom = 24.dp + navigationBarBottom()
        ).plus(padding)
    ) {
        val detail = state.detail
        when (contentState) {
            "Loading" -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingMark(size = 96.dp)
                }
            }
            "Failed" -> item(key = "error") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = ZoneGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { ErrorPanel(error = it, onRetry = { viewModel.load(id, force = true) }) }
                    // The page itself may still open in a browser.
                    BoldButton(onClick = { LinkRouter.openOutside(context, JobsParser.jobUrl(id)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open on LinkedIn")
                    }
                }
            }
            // The offer may be gone from the state while its screen is still fading out: nothing is drawn then.
            else -> if (detail != null) {
                item(key = "head") {
                    Head(
                        detail,
                        onApply = { LinkRouter.openOutside(context, detail.url) },
                        onOpenCompany = onOpenCompany
                    )
                }
                if (detail.criteria.isNotEmpty()) item(key = "criteria") { Criteria(detail.criteria) }
                if (detail.description.isNotBlank()) item(key = "description") { Description(detail.description) }
            }
        }
    }
    }
    }
}

@Composable
private fun Head(
    detail: JobDetail,
    onApply: () -> Unit,
    onOpenCompany: (handle: String, kind: AccountKind) -> Unit
) {
    // The company is a tap when its address is a page the app shows (a company, a school or a showcase page); the
    // page's kind rides with the handle, as Accounts opens a profile. Otherwise it stays plain text.
    val company = LinkedInLink.parse(detail.companyUrl) as? LinkedInLink.Company
    Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Centred: the offer's name and owner are data, not rows of a list.
            Text(detail.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (detail.company.isNotBlank()) {
                if (company != null) {
                    // A small zone of its own, so that it reads as a tap (the validated mockup).
                    Zone(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        onClick = { onOpenCompany(company.slug, AccountKind.ofSegment(company.segment)) }
                    ) {
                        Text(
                            detail.company,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Text(detail.company, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                }
            }
            // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
            Text(
                listOfNotNull(detail.location.takeIf { it.isNotBlank() }, detail.postedAgo, detail.applicants.takeIf { it.isNotBlank() })
                    .joinToString(", "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            BoldButton(onClick = onApply, filled = true, modifier = Modifier.padding(top = 8.dp)) {
                Text("View and apply on LinkedIn")
            }
        }
    }
}

@Composable
private fun Criteria(criteria: List<Pair<String, String>>) {
    Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            criteria.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Description(text: String) {
    Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }
}
