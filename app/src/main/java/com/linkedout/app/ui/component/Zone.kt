package com.linkedout.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one shape every surface in this app is cut from.
 *
 * A zone is a rounded container that stands out by its fill alone. Everything
 * that holds content goes through here: post cards, quotes, link previews,
 * polls, notes, pictures, banners and the error panel. One knob, one look.
 *
 * No outline by default. An edge on every card made every card look
 * emphasised, and then the one thing that deserves emphasis, a post that
 * arrived since the last visit, had no louder register left to use. So the
 * outline exists only there, which is why it is an optional colour and not a
 * constant baked into the drawing.
 */

/** A card and anything that stands alone on the page. */
val ZoneShape = RoundedCornerShape(24.dp)

/** A zone nested inside a card. Smaller radius, so the two corners agree. */
val InnerZoneShape = RoundedCornerShape(16.dp)

/**
 * The only outline in the app: a post that arrived since the last visit and
 * has not been passed yet. Two points was enough as soon as nothing else on
 * the screen had an edge to compete with.
 */
val UnreadOutline: Dp = 2.dp

/** Space a card leaves around itself, so two cards never touch. */
val ZoneGap: Dp = 6.dp

@Composable
fun Zone(
    modifier: Modifier = Modifier,
    shape: Shape = ZoneShape,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    /** Null, and the zone has no edge at all. */
    outline: Color? = null,
    outlineWidth: Dp = UnreadOutline,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val border = outline?.let { BorderStroke(outlineWidth, it) }
    // Two calls rather than one with a null click: the clickable Surface is a
    // different overload, and giving it a no-op lambda would add a ripple and
    // a semantics node to a card that is not meant to be tapped.
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            content = content
        )
    }
}
