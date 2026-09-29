package com.linkedout.app.ui.component

import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassZone
import com.linkedout.app.ui.theme.zone
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A zone of the app: a card, a banner, a section, a row. Tinted by Material
 * You, and made of glass when glass is on (see ui/glass): then the fill, the
 * outline and the elevation give way to the glass, whose rim draws the edge.
 * Every zone goes through here, so the whole app changes at once.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun ZoneSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.zone,
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glass = LocalGlass.current
    val look = if (glass == null) modifier else modifier.glassZone(shape, glass)
    val fill = if (glass == null) color else Color.Transparent
    val edge = if (glass == null) border else null
    val tonal = if (glass == null) tonalElevation else 0.dp
    val shadow = if (glass == null) shadowElevation else 0.dp
    if (onClick == null) {
        Surface(look, shape, fill, border = edge, tonalElevation = tonal, shadowElevation = shadow, content = content)
    } else {
        Surface(onClick, look, shape = shape, color = fill, border = edge, tonalElevation = tonal, shadowElevation = shadow, content = content)
    }
}
