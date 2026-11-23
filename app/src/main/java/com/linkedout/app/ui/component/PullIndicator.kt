package com.linkedout.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The pull to refresh indicator, with the app's own loading mark in place of
 * the spinner: the mark follows the pull, as far as it has gone, then plays
 * on its own while the refresh runs. The mark is 72 dp and stands alone on
 * the page, with no disc, glass or shadow behind it. Placed like the standard
 * indicator, sliding down from the top edge of its box.
 *
 * The hand feels the threshold: a crisp tick when the pull has gone far
 * enough to refresh on release, a faint one if it is brought back short.
 * Only the reader's own drag does that, not the indicator settling or
 * leaving on its own.
 *
 * Each app draws its mark in LoadingMark, with the same parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val threshold = PullToRefreshDefaults.PositionalThreshold
    val haptics = rememberHaptics()
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        var reached = false
        snapshotFlow { (state.distanceFraction >= 1f) to (state.isAnimating || refreshing) }
            .collect { (over, settling) ->
                if (!settling && over != reached) haptics.threshold(over)
                reached = over
            }
    }
    // The pull is read in the layer and in the mark's draw, never in composition: a drag frame redraws, it does not
    // recompose. One alpha: the pull's own fade-in, or the refresh holding it at 1 (animated, so it never snaps).
    // Ported from MTGA's PullIndicator, whose layer reads state.distanceFraction:
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/PullIndicator.kt#PullIndicator
    // not ported: FeatherRing, PullRing, PullRevealBox, Scrim, animateRing, animateIn, MBBackgroundView, MBProgressHUDAnimation
    // (the user keeps LinkedOut's own pull animation, 2026-11-22), countLabel
    val held = animateFloatAsState(if (isRefreshing) 1f else 0f, label = "pull held")
    Box(
        modifier.graphicsLayer {
            val fraction = state.distanceFraction
            translationY = fraction * threshold.toPx() - this.size.height
            alpha = max((fraction * 2f).coerceIn(0f, 1f), held.value)
        }
    ) {
        // The mark alone, drawn straight on the page: no disc, no glass, no shadow.
        LoadingMark(size = 72.dp, running = isRefreshing, progress = { state.distanceFraction.coerceIn(0f, 1f) })
    }
}
