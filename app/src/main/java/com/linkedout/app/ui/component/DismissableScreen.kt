package com.linkedout.app.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

/**
 * A screen that follows the thumb when it is being dismissed.
 *
 * Written because the navigation library's own predictive back never moved
 * anything here. A screencast settled it: the screen stood perfectly still
 * for the whole drag, the system drew its own edge affordance, and an
 * animation played only once the thumb had gone. Three attempts at coaxing
 * the host into seeking its transition changed nothing, so the gesture is
 * taken directly instead. [PredictiveBackHandler] hands over a flow of
 * progress from the moment the thumb starts, and that progress drives a
 * layer, which is the cheapest thing a phone can animate.
 *
 * The screen slides right whatever edge the gesture came from, because the
 * pop transition behind it slides right too, and the two have to agree or the
 * page jumps back to the middle before leaving.
 *
 * The final position is held rather than reset when the gesture completes.
 * The page is already off the side by then, so the host's own pop animation
 * plays on something nobody can see, instead of snapping it back into view
 * for one last slide.
 */
@Composable
fun DismissableScreen(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = !leaving) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress.coerceIn(0f, 1f)) }
            leaving = true
            progress.snapTo(1f)
            onBack()
        } catch (cancelled: CancellationException) {
            // The thumb came back. Settle where it started, nothing else to do.
            progress.animateTo(0f, tween(SETTLE_MS))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val moved = progress.value
                translationX = size.width * TRAVEL * moved
                val shrunk = 1f - SHRINK * moved
                scaleX = shrunk
                scaleY = shrunk
            }
    ) {
        content()
    }
}

/** How far across its own width the page has travelled at full progress. */
private const val TRAVEL = 0.4f

/** How much it shrinks on the way, which is what reads as "behind". */
private const val SHRINK = 0.08f

private const val SETTLE_MS = 180
