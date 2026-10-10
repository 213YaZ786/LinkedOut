package com.linkedout.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * A picture the user can zoom: pinch to zoom, pan while zoomed, double tap
 * to zoom where tapped or back. At rest a single finger is left alone, so a
 * pager or a swipe to close around it still gets it. [content] draws the
 * picture filling the box; the zoom applies to it.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun Zoomable(
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun set(newScale: Float, newOffset: Offset) {
        val was = scale.value > 1f
        scope.launch { scale.snapTo(newScale) }
        offset = if (newScale <= 1f) Offset.Zero else clamp(newOffset, newScale)
        if (was != newScale > 1f) onZoomChanged(newScale > 1f)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        haptics.tick()
                        val zoomed = scale.value > 1f
                        val target = if (zoomed) 1f else DOUBLE_TAP_SCALE
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val goal = if (zoomed) Offset.Zero else clamp((centre - tap) * (DOUBLE_TAP_SCALE - 1f), DOUBLE_TAP_SCALE)
                        val from = offset
                        val start = scale.value
                        scope.launch {
                            // The zoom springs in towards the tapped point, and back.
                            scale.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 400f)) {
                                val t = ((value - start) / (target - start)).coerceIn(0f, 1f)
                                offset = from + (goal - from) * t
                            }
                            offset = goal
                        }
                        onZoomChanged(!zoomed)
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale.value > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            set((scale.value * zoom).coerceIn(1f, MAX_SCALE), offset + pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.x
                    translationY = offset.y
                }
        ) { content() }
    }
}

private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 5f
