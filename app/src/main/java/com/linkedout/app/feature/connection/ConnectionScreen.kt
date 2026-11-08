package com.linkedout.app.feature.connection

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import com.linkedout.app.feature.BarAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.plus
import com.linkedout.app.ui.component.scrolledFromTop
import com.linkedout.app.ui.component.underFloatingTopBar
import com.linkedout.app.ui.component.ZoneSurface
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.navigation.LocalReadableInset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.network.HostThrottle
import com.linkedout.app.data.linkedin.LinkedInHost
import com.linkedout.app.data.linkedin.ProfilePageParser
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.compose.koinInject

/**
 * What this app talks to, and whether it is currently allowed to.
 *
 * This replaces Diagnostics, which existed to compare a pool of interchangeable
 * servers and pick a healthy one. There is no pool now and nothing to choose
 * between, so there is no health to report in that sense. What is worth showing
 * is the opposite: that there is exactly one host, named, and that nothing else
 * is contacted.
 *
 * Deliberately thin for now. The guest gateway is the next piece of work, and
 * its state, whether the referrer retry was needed and whether the offscreen
 * browser had to take over, is what belongs on this screen once it exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(onBack: () -> Unit) {
    val throttle: HostThrottle = koinInject()
    val cooldownMs = throttle.cooldownRemainingMs(LinkedInHost.HOST)

    // Ported from MTGA's FeedScreen: the FloatingTopBar as the Scaffold's topBar, the list under it with
    // underFloatingTopBar
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen).
    Scaffold(
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // The list owns the navigation bar in its content padding, the bar clears the status bar itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FloatingTopBar(
                title = { BarTitle("Connection") },
                navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onBack) }
            )
        }
    ) { padding ->
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().underFloatingTopBar(
            barHeight = padding.calculateTopPadding(),
            scrolled = listState::scrolledFromTop
        ),
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current,
            bottom = navigationBarBottom()
        ).plus(padding)
    ) {
        item(key = "host") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZoneSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(LinkedInHost.HOST, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (cooldownMs > 0) {
                            "Pausing for ${cooldownMs / 1000} more seconds after a rate limit"
                        } else {
                            "Ready"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "LinkedOut contacts this one host and nothing else. " +
                    "Pages are read as a logged out visitor, so what you see here " +
                    "is what anyone would see without an account.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Profile selector set ${ProfilePageParser.SELECTOR_SET_VERSION}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        }
    }
    }
}
