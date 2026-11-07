package com.linkedout.app.feature.jobs

import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.ui.component.LoadingMark
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.model.ExperienceLevelFilters
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobFilters
import com.linkedout.app.core.model.OnSiteOrRemoteFilters
import com.linkedout.app.core.model.TimeFilters
import com.linkedout.app.core.model.TypeFilters
import androidx.compose.runtime.saveable.rememberSaveable
import com.linkedout.app.data.marks.SavedJob
import com.linkedout.app.data.marks.SavedJobs
import com.linkedout.app.navigation.LocalReadableInset
import org.koin.compose.koinInject
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.CheckableDropdownMenuItem
import com.linkedout.app.ui.component.DropdownMenuGroup
import com.linkedout.app.ui.component.DropdownMenuItem
import com.linkedout.app.ui.component.GlassMenu
import com.linkedout.app.ui.component.MenuPill
import com.linkedout.app.ui.component.SelectableDropdownMenuItem
import com.linkedout.app.ui.component.SubmenuHeader
import com.linkedout.app.ui.component.ValueMenuItem
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.LocalDockPadding
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * LinkedIn's public job offers, searched without an account.
 *
 * The search sits in its own zone at the top and the offers follow as cards.
 * Nothing is requested until the reader searches, and further pages come on a
 * tap rather than on a scroll: every request here is shared with the profile
 * reads, which LinkedIn is quick to refuse.
 */
@Composable
fun JobsScreen(
    onOpenJob: (JobCard) -> Unit,
    viewModel: JobsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    // The saved offers are shown in place of the results until the Saved pill is tapped again or a search is made.
    var showSaved by rememberSaveable { mutableStateOf(false) }
    val savedJobs: SavedJobs = koinInject()
    val saved by savedJobs.saved.collectAsState()
    val search = {
        focus.clearFocus()
        showSaved = false
        viewModel.search()
    }
    // The menu edits a draft of the filters and applies it when it closes: each applied change is a request to LinkedIn,
    // and two toggles in a row must be one.
    var filtersOpen by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf<FilterKind?>(null) }
    var draft by remember { mutableStateOf(state.filters) }
    val closeFilters = {
        filtersOpen = false
        submenu = null
        // A changed filter searches again, which brings the results back.
        if (draft != state.filters && state.searched != null) showSaved = false
        viewModel.setFilters(draft)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current,
            top = statusBarTop(),
            bottom = LocalDockPadding.current + 16.dp
        )
    ) {
        item(key = "banner") {
            ScreenBanner(
                title = "Jobs",
                subtitle = state.searched?.let { (keywords, location) ->
                    if (location.isBlank()) keywords else "$keywords, $location"
                }
            )
        }

        item(key = "search") {
            Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SearchField(
                        value = state.keywords,
                        onValueChange = viewModel::setKeywords,
                        placeholder = "Job title, skill or company",
                        imeAction = ImeAction.Next,
                        onAction = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                    )
                    SearchField(
                        value = state.location,
                        onValueChange = viewModel::setLocation,
                        placeholder = "City, region or country",
                        imeAction = ImeAction.Search,
                        onAction = search
                    )
                    BoldButton(
                        onClick = search,
                        filled = true,
                        enabled = state.keywords.isNotBlank() && !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(LinkedOutIcons.Search, contentDescription = null)
                        Text("Search", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // JobsScreen's list, with the filters' pill and the count between the search pane and the offers:
        // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/jobs/JobsScreen.kt#JobsScreen
        // One pill under the search pane, centred: Filters, with the number set, in the accent while any is. It opens the
        // menu under it. The Box is the menu's anchor.
        item(key = "filters") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box {
                    MenuPill(
                        icon = LinkedOutIcons.Tune,
                        label = if (state.filters.count > 0) "Filters, ${state.filters.count}" else "Filters",
                        expanded = filtersOpen,
                        accent = state.filters.count > 0,
                        onClick = {
                            draft = state.filters
                            filtersOpen = true
                        }
                    )
                    GlassMenu(
                        expanded = filtersOpen,
                        onDismissRequest = closeFilters,
                        onBack = { if (submenu != null) submenu = null else closeFilters() }
                    ) {
                        JobFiltersMenu(
                            filters = draft,
                            submenu = submenu,
                            onSubmenu = { submenu = it },
                            onFilters = { draft = it },
                            // A choice made in a submenu closes the menu and searches, as iOS's pull-down menus do.
                            onPick = {
                                draft = it
                                closeFilters()
                            },
                            onClear = {
                                draft = JobFilters()
                                closeFilters()
                            }
                        )
                    }
                }
                // Saved, beside Filters (the validated mockup): the offers kept on this phone, in the accent while shown.
                MenuPill(
                    icon = LinkedOutIcons.Bookmark,
                    label = "Saved",
                    expanded = showSaved,
                    accent = showSaved,
                    onClick = { showSaved = !showSaved }
                )
            }
        }

        // What LinkedIn says of the results, as it words it, plain text: it does nothing, so it has no zone.
        state.count?.takeIf { !showSaved && !state.loading && state.cards.isNotEmpty() }?.let { count ->
            item(key = "count") {
                Text(
                    count,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        }

        state.error?.takeIf { !showSaved }?.let { error ->
            item(key = "error") {
                ErrorPanel(
                    error = error,
                    onRetry = if (state.cards.isEmpty()) viewModel::search else viewModel::loadMore,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = ZoneGap)
                )
            }
        }

        when {
            showSaved -> {
                if (saved.jobs.isEmpty()) item(key = "no-saved") { Hint("No saved offer") }
            }
            state.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingMark(size = 96.dp)
                }
            }
            state.searched == null -> item(key = "hint") {
                Hint(
                    "Public offers from LinkedIn, read without an account. " +
                        "What you search is sent to LinkedIn, as on its own site, and stays on this phone otherwise."
                )
            }
            state.cards.isEmpty() && state.error == null -> item(key = "none") {
                Hint("No offer matches. Fewer words or a wider place usually help.")
            }
        }

        // The saved cards open the offer as the others.
        items(if (showSaved) saved.jobs.map(SavedJob::toCard) else state.cards, key = { it.id }) { card ->
            JobCardZone(card = card, onClick = { onOpenJob(card) })
        }

        if (!showSaved && state.cards.isNotEmpty()) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loadingMore -> LoadingMark(size = 96.dp)
                        state.endReached -> Text(
                            "No more offers for this search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> BoldButton(onClick = viewModel::loadMore) { Text("More offers") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onAction: () -> Unit
) {
    GlassSearchField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onSearch = { onAction() }),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun JobCardZone(card: JobCard, onClick: () -> Unit) {
    Zone(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(url = card.logoUrl, name = card.company.ifBlank { card.title }, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.company.isNotBlank()) {
                    Text(card.company, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Joined with a comma, as AOSP Dialer's CallLogEntryText.java joinSecondaryTextComponents (https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/main/java/com/android/dialer/calllogutils/CallLogEntryText.java).
                Text(
                    listOfNotNull(card.location.takeIf { it.isNotBlank() }, postedLabel(card.postedDate)).joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The first badge LinkedIn gives the offer ("Actively hiring"), one line in the accent.
                card.badges.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
        }
    }
}

// The filters' menu: one row per argument of search_linkedin_jobs that LinkedIn's guest filter bar has (date_posted,
// workplace, experience, job_type, easy_apply, under_10_applicants), in the three groups of Material 3 Expressive's
// grouped menu, apart by GroupSpacing. A row that has choices shows its value and a chevron and opens them in place
// ([submenu] set), where the row it came from is the header with Back and the choices are SelectableDropdownMenuItems, one
// selected; Easy Apply and Under 10 applicants are CheckableDropdownMenuItems; Clear filters is the last group, in the
// error colour. [filters] is the draft the menu edits, applied when it closes.
// https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#search_linkedin_jobs
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
// not ported: dropdownmenugroup (GlassMenu.kt's, called here for each group)
// not ported: search_linkedin_jobs (JobsSource's, the search itself)
// not ported: DropdownMenuPopup (GlassMenu, which holds this content), MenuGroupShapes and MenuDefaults.groupShape
// (segmentedShape gives the shape by index and count), keywords, location, geo_id, strict_location, min_salary, sort,
// start, limit, max_tokens, timeout, browser_solver (not filters)
@Composable
private fun JobFiltersMenu(
    filters: JobFilters,
    submenu: FilterKind?,
    onSubmenu: (FilterKind?) -> Unit,
    onFilters: (JobFilters) -> Unit,
    onPick: (JobFilters) -> Unit,
    onClear: () -> Unit
) {
    if (submenu == null) {
        DropdownMenuGroup(index = 0, count = 3) {
            ValueMenuItem("Date posted", filters.date_posted.label) { onSubmenu(FilterKind.date_posted) }
            ValueMenuItem("Workplace", filters.workplace.label) { onSubmenu(FilterKind.workplace) }
            ValueMenuItem("Experience", filters.experience.label) { onSubmenu(FilterKind.experience) }
            ValueMenuItem("Job type", filters.job_type.label) { onSubmenu(FilterKind.job_type) }
        }
        DropdownMenuGroup(index = 1, count = 3) {
            CheckableDropdownMenuItem(filters.easy_apply, { onFilters(filters.copy(easy_apply = it)) }, "Easy Apply")
            CheckableDropdownMenuItem(
                filters.under_10_applicants,
                { onFilters(filters.copy(under_10_applicants = it)) },
                "Under 10 applicants"
            )
        }
        DropdownMenuGroup(index = 2, count = 3) {
            DropdownMenuItem(text = "Clear filters", color = MaterialTheme.colorScheme.error, onClick = onClear)
        }
        return
    }
    DropdownMenuGroup(index = 0, count = 2) { SubmenuHeader(title = submenu.label, onBack = { onSubmenu(null) }) }
    DropdownMenuGroup(index = 1, count = 2) {
        when (submenu) {
            FilterKind.date_posted -> TimeFilters.entries.forEach {
                SelectableDropdownMenuItem(filters.date_posted == it, { onPick(filters.copy(date_posted = it)) }, it.label)
            }
            FilterKind.workplace -> OnSiteOrRemoteFilters.entries.forEach {
                SelectableDropdownMenuItem(filters.workplace == it, { onPick(filters.copy(workplace = it)) }, it.label)
            }
            FilterKind.experience -> ExperienceLevelFilters.entries.forEach {
                SelectableDropdownMenuItem(filters.experience == it, { onPick(filters.copy(experience = it)) }, it.label)
            }
            FilterKind.job_type -> TypeFilters.entries.forEach {
                SelectableDropdownMenuItem(filters.job_type == it, { onPick(filters.copy(job_type = it)) }, it.label)
            }
        }
    }
}

/** The filters that open their choices in place, with the words of their row. */
@Suppress("EnumEntryName")
private enum class FilterKind(val label: String) {
    date_posted("Date posted"), workplace("Workplace"), experience("Experience"), job_type("Job type")
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)
    )
}

/** The day the card gives, in the phone's own date format. */
private fun postedLabel(isoDay: String?): String? =
    isoDay?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }
