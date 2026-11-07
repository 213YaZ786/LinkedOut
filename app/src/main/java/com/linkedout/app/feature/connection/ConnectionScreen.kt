package com.linkedout.app.feature.connection

import androidx.compose.foundation.shape.RoundedCornerShape
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.ZoneSurface
import com.linkedout.app.ui.component.navigationBarBottom
import com.linkedout.app.ui.component.statusBarTop
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
@Composable
fun ConnectionScreen(onBack: () -> Unit) {
    val throttle: HostThrottle = koinInject()
    val cooldownMs = throttle.cooldownRemainingMs(LinkedInHost.HOST)

    // The page's ground is painted under the whole app, see MainActivity. The whole page is one list, the banner
    // of the profile page its first row, so it scrolls with the rest
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#L173-L206
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LocalReadableInset.current,
            end = LocalReadableInset.current,
            top = statusBarTop(),
            bottom = navigationBarBottom()
        )
    ) {
        item(key = "banner") {
            ScreenBanner(
                title = "Connection",
                leading = {
                    BannerAction(
                        icon = LinkedOutIcons.ArrowBack,
                        label = "Back",
                        onClick = onBack
                    )
                }
            )
        }

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
