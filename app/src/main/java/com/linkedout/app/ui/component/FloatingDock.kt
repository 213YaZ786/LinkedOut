package com.linkedout.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Space the floating dock covers at the bottom of the screen. Scrolling
 * screens add it to their bottom padding so their last item is not hidden.
 */
val LocalDockPadding = staticCompositionLocalOf { 0.dp }

/** Height of the dock plus the gap under it, for [LocalDockPadding]. */
val DockClearance: Dp = 96.dp

/**
 * Width the vertical dock takes on the left of a wide window: the 16 dp gap
 * to the edge, the 64 dp pill, and 16 dp of air before the content.
 */
val SideDockClearance: Dp = 96.dp

data class DockItem(val icon: ImageVector, val label: String)

/**
 * A floating pill with one icon per tab. The highlight follows the finger
 * while swiping between tabs, so the dock and the pages always agree.
 *
 * [position] is the current page plus how far the swipe has moved toward the
 * next one, from 0 to the last index.
 *
 * [vertical] stacks the same pill upright, for the left edge of a tablet or
 * a phone on its side. Same shape, colours and icons, so both read as one app.
 */
@Composable
fun FloatingDock(
    items: List<DockItem>,
    position: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    // Each item is long along the dock and short across it, in both directions.
    val alongDock = 64.dp
    val acrossDock = 48.dp
    val itemWidth = if (vertical) acrossDock else alongDock
    val itemHeight = if (vertical) alongDock else acrossDock
    val stepPx = with(LocalDensity.current) { alongDock.toPx() }
    // No extra animation here: the pager already animates taps, and while
    // swiping the highlight must stay exactly under the finger.
    val animated = position

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Box(Modifier.padding(8.dp)) {
            Box(
                Modifier
                    .offset {
                        val step = (animated * stepPx).roundToInt()
                        if (vertical) IntOffset(0, step) else IntOffset(step, 0)
                    }
                    .size(itemWidth, itemHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )
            val buttons: @Composable () -> Unit = {
                items.forEachIndexed { index, item ->
                    val closeness = (1f - abs(animated - index)).coerceIn(0f, 1f)
                    val tint = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        closeness
                    )
                    Box(
                        modifier = Modifier
                            .size(itemWidth, itemHeight)
                            .clip(CircleShape)
                            .clickable(onClickLabel = item.label, role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = tint)
                    }
                }
            }
            if (vertical) Column { buttons() } else Row { buttons() }
        }
    }
}
