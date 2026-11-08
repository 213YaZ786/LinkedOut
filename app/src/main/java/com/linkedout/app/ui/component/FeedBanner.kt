package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A feed screen's top row (Home, Accounts), one header row as Bluesky's HomeHeaderLayoutMobile: a slot, the centre
 * taking the rest (flex_1, align_center), a slot. The centre is the feed pill (or the plain title) with the subtitle
 * beside it, 12 dp apart, the pair centred between the two action slots (both slots keep their room, so the pair is
 * centred on the screen). A pair too long for the line drops the subtitle under the centre instead of cutting it.
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * not ported: HomeHeaderLayoutMobile (React Native's file; FeedBanner is its row in Compose, the centre open to a composable)
 * not ported: ScreenBanner (the title row stays in its own file; FeedBanner is the same row with a free centre)
 * not ported: BannerAction (used as it is), the title's two lines with TextAutoSize (one line, ellipsis), BannerGap and
 * BannerActionSize (private there, copied below), StatusBarFade, the logo and the hide on scroll (Bluesky's header only)
 */
@Composable
fun FeedBanner(
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
                // One line: the pill and the age centred on each other (a pill has no text baseline to share), the pair centred.
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

/** Space the banner leaves above and below itself, as ScreenBanner's. */
private val BannerGap = 8.dp

/** Both slots of the banner, as ScreenBanner's. */
private val BannerActionSize = 44.dp
