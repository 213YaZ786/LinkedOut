package com.linkedout.app.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Ported from MTGA's screens, where FloatingTopBar's back and action are Material IconButtons
// (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/FeedScreen.kt#FeedScreen).
// The guard refuses a new Material IconButton in app code, and a button inside the bar's glass zone stays a
// transparent surface, so the nine screens share this one: a 48 dp circle target, the icon 24 dp.

/** A round action in a [com.linkedout.app.ui.component.FloatingTopBar] slot (Back, Refresh, Share, Copy). */
@Composable
fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Unspecified takes the bar's content colour, the accent says a state is on (the kept offer's bookmark). */
    tint: Color = Color.Unspecified
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint.takeOrElse { LocalContentColor.current }, modifier = Modifier.size(24.dp))
        }
    }
}
