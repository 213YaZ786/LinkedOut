package com.linkedout.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The mark of a post that arrived since the last visit and has not been
 * scrolled past yet: a small dot in the accent, at the end of the author's line. It
 * shrinks away once the post is passed. A border said the same, but glass
 * draws no borders.
 */
@Composable
fun NewDot(visible: Boolean, modifier: Modifier = Modifier) {
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedVisibilitySamples.kt#AnimatedVisibilityWithBooleanVisibleParamNoReceiver
    // The slot (the caller's [modifier], its start padding, and the dot) opens or closes first, so the row
    // makes room gradually; the dot scales and fades from its own centre inside it (animateEnterExit),
    // never moving over the name.
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(200)),
        exit = shrinkHorizontally(tween(200, delayMillis = 100))
    ) {
        Box(modifier.size(10.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = scaleIn(tween(200, delayMillis = 60)) + fadeIn(tween(200, delayMillis = 60)),
                        exit = scaleOut(tween(120)) + fadeOut(tween(120))
                    )
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}
