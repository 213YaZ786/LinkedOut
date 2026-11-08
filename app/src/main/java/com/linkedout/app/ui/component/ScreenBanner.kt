package com.linkedout.app.ui.component

import com.linkedout.app.ui.glass.glassZone
import com.linkedout.app.ui.glass.LocalGlass
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a screen opens with: its name in the middle, one round action on
 * each side.
 *
 * It is an item of the list, not a bar over it. It leaves when the reader
 * scrolls down and comes back only once they are back at the top. A bar that
 * reappears on the first upward flick steals the line being read.
 *
 * The title is plain text in the accent, drawn straight on the page, with no
 * zone (the user, 2026-11-07): a zone means a tap, and a title is not tapped.
 * Only the round actions on each side are glass, each its own.
 *
 * Both slots reserve the same width whether or not they hold anything, so the
 * title is centred on the screen and not on what is left of the row.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun ScreenBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    // https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/FloatingPane.kt#FloatingTop
    // The row has a minimum height only, so a title on two lines makes it grow.
    // Not ported: LocalTitleCollapse's cross fade (no large title here, the banner scrolls with its list),
    // the `center` slot (no caller needs it).
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both action slots keep their room, so the name stays centred.
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        // A gap on each side; a long name wraps between words on two lines, and a word too wide for the
        // room shrinks as the last resort: Compose's TextAutoSize.StepBased
        // (https://developer.android.com/develop/ui/compose/text/autosize), never an ellipsis first.
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val titleStyle = MaterialTheme.typography.headlineSmall
            BasicText(
                title,
                // Every title in the accent (the user, 2026-10-31), as SettingsTiles' SettingsSectionTitle.
                style = titleStyle.copy(color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = titleStyle.fontSize, stepSize = 0.5.sp),
                modifier = Modifier.semantics { heading() }
            )
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
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/** A round action in a banner slot. Filled, because a bare icon reads as decoration. */
@Composable
fun BannerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = Color.Unspecified
) {
    // Through the view: the constant has existed far longer than the
    // minimum version, and the system setting still decides.
    val view = LocalView.current
    // In glass, a small pane of its own on the banner's glass, the icon in
    // the accent unless a tint is asked for; otherwise filled, with the edge every action carries.
    // A tint left Unspecified takes the default of its context, as Material 3's Text colour (takeOrElse):
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Text.kt#Text
    val glass = LocalGlass.current
    // In glass, the 48 dp touch target outside the pane (GlassTarget), so the ripple fills the glass.
    GlassTarget(Modifier) {
        Surface(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
            shape = CircleShape,
            color = if (glass == null) container else Color.Transparent,
            border = if (glass == null) boldBorder() else null,
            modifier = if (glass == null) Modifier else Modifier.glassZone(CircleShape, glass, lens = 1f)
        ) {
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (glass == null) tint.takeOrElse { MaterialTheme.colorScheme.onSecondaryContainer } else tint.takeOrElse { MaterialTheme.colorScheme.primary }, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Space the banner leaves above and below itself, so it never touches what follows. */
private val BannerGap = 8.dp

/** Both slots of the banner, so an empty one still holds the title centred. */
private val BannerActionSize = 44.dp
