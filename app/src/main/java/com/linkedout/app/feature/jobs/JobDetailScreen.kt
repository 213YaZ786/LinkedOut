package com.linkedout.app.feature.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.data.linkedin.JobsParser
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.ErrorPanel
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * One offer: who, where, what kind of contract, and the whole description,
 * read from its guest page. Applying happens on LinkedIn, which is where the
 * button leads, outside the app.
 */
@Composable
fun JobDetailScreen(
    id: String,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.load(id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = LocalReadableInset.current, end = LocalReadableInset.current, bottom = 24.dp)
    ) {
        item(key = "banner") {
            ScreenBanner(
                title = "Offer",
                leading = { BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = onBack) }
            )
        }

        val detail = state.detail
        when {
            state.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            detail == null -> item(key = "error") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = ZoneGap), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.error?.let { ErrorPanel(error = it, onRetry = { viewModel.load(id, force = true) }) }
                    // The page itself may still open in a browser.
                    BoldButton(onClick = { LinkRouter.openOutside(context, JobsParser.jobUrl(id)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open on LinkedIn")
                    }
                }
            }
            else -> {
                item(key = "head") { Head(detail, onApply = { LinkRouter.openOutside(context, detail.url) }) }
                if (detail.criteria.isNotEmpty()) item(key = "criteria") { Criteria(detail.criteria) }
                if (detail.description.isNotBlank()) item(key = "description") { Description(detail.description) }
            }
        }
    }
}

@Composable
private fun Head(detail: JobDetail, onApply: () -> Unit) {
    Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Centred: the offer's name and owner are data, not rows of a list.
            Text(detail.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (detail.company.isNotBlank()) {
                Text(detail.company, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
            Text(
                listOfNotNull(detail.location.takeIf { it.isNotBlank() }, detail.postedAgo).joinToString(" · "),
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
