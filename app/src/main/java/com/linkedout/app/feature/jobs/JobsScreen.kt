package com.linkedout.app.feature.jobs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BoldButton
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
    val search = {
        focus.clearFocus()
        viewModel.search()
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

        state.error?.let { error ->
            item(key = "error") {
                ErrorPanel(
                    error = error,
                    onRetry = if (state.cards.isEmpty()) viewModel::search else viewModel::loadMore,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = ZoneGap)
                )
            }
        }

        when {
            state.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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

        items(state.cards, key = { it.id }) { card ->
            JobCardZone(card = card, onClick = { onOpenJob(card) })
        }

        if (state.cards.isNotEmpty()) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loadingMore -> CircularProgressIndicator()
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
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(placeholder) },
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
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
                Text(
                    listOfNotNull(card.location.takeIf { it.isNotBlank() }, postedLabel(card.postedDate)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)
    )
}

/** The day the card gives, in the phone's own date format. */
private fun postedLabel(isoDay: String?): String? =
    isoDay?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }
