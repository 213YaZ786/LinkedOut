package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The pull to refresh indicator, with the app's own loading mark in place of
 * the spinner: the mark follows the pull, as far as it has gone, then plays
 * on its own while the refresh runs. Placed like the standard indicator,
 * sliding down from the top edge of its box in a small raised disc.
 *
 * Each app draws its mark in LoadingMark, with the same parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullIndicator(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val threshold = PullToRefreshDefaults.PositionalThreshold
    val fraction = state.distanceFraction
    Box(
        modifier.graphicsLayer {
            translationY = fraction * threshold.toPx() - this.size.height
            alpha = if (isRefreshing) 1f else (fraction * 2f).coerceIn(0f, 1f)
        }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                LoadingMark(size = 30.dp, running = isRefreshing, progress = fraction.coerceIn(0f, 1f))
            }
        }
    }
}
