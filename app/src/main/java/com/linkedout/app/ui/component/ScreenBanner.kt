package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The zone a screen opens with: its name in the middle, one round action on
 * each side.
 *
 * It is an item of the list, not a bar over it. It leaves when the reader
 * scrolls down and comes back only once they are back at the top. A bar that
 * reappears on the first upward flick steals the line being read, which is
 * why the enter-always behaviour this replaced was wrong.
 *
 * Both sides reserve the same width whether or not they hold anything, so the
 * title is centred on the screen and not on what is left of the row.
 */
@Composable
fun ScreenBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Zone(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = ZoneGap)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                leading?.invoke()
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                trailing?.invoke()
            }
        }
    }
}

/** Both slots of the banner, so an empty one still holds the title centred. */
private val BannerActionSize = 44.dp

/** A round action in a banner slot. Filled, because a bare icon reads as decoration. */
@Composable
fun BannerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container
    ) {
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}
