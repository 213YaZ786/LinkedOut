package com.linkedout.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp

/**
 * The picture for an account that has none.
 *
 * Not a failure state. A LinkedIn member who never uploaded a photograph is
 * shown a silhouette on LinkedIn itself, so the parser finding no address is
 * the truth about the account rather than a selector that missed. Drawing it
 * here costs no request, works offline, and cannot be refused by the CDN the
 * way a signed avatar address can.
 *
 * Two shapes rather than their artwork: a head and a pair of shoulders, in the
 * theme's own colours, so it sits in a dark theme as well as a light one and
 * nothing of theirs is carried in the repository.
 */
@Composable
fun AvatarGhost(diameter: Dp) {
    val head = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val shoulders = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Canvas(Modifier.size(diameter)) {
        val width = size.width
        val height = size.height
        // Drawn past the bottom edge on purpose. The caller clips to a circle
        // or a rounded square, so the shoulders meet the edge rather than
        // floating inside it.
        drawOval(
            color = shoulders,
            topLeft = Offset(width * 0.07f, height * 0.60f),
            size = Size(width * 0.86f, height * 0.78f)
        )
        drawCircle(
            color = head,
            radius = width * 0.21f,
            center = Offset(width / 2f, height * 0.38f)
        )
    }
}
