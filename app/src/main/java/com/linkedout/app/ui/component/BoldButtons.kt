package com.linkedout.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Buttons that stand out: a two point edge in the primary colour around
 * every action a screen offers, so an action reads as something to press and
 * not as more text. Dialog buttons and the icons of a top bar keep Material's
 * plain style, where an edge on everything would leave nothing louder.
 *
 * Shared with MTGA, keep the two copies identical apart from the package.
 */
val BoldEdge: Dp = 2.dp

@Composable
fun boldBorder(enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary): BorderStroke =
    BorderStroke(BoldEdge, if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

/** [filled] keeps a tonal fill inside the edge, for the one main action of a place. */
@Composable
fun BoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = if (filled) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.outlinedButtonColors(),
        border = boldBorder(enabled),
        content = content
    )
}

/** A tonal icon button with the same edge. [edge] turns to the error colour for a failing state. */
@Composable
fun BoldIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    edge: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        border = boldBorder(enabled, edge),
        content = content
    )
}
