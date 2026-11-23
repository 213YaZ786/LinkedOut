package com.linkedout.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The glass round action of a page, which the finger can move anywhere inside the page's safe area (the bars
 * apart, 16 dp from every edge). Lifted while held (a little larger, a soft shadow), on release it glides on a
 * spring to the nearer side, left or right, at the height it was left. Its place is kept as a snap fraction of
 * the movement bounds, so it fits another window size or a turned phone; null is the bottom right corner.
 * A tap calls [onClick]; TalkBack reads [label] and offers [moveLabel] to send it to the other side.
 *
 * Its state and drag follow the DraggableSample (offsetPosition updated by the drag's delta, read in the offset
 * lambda), on both axes at once by draggable2D.
 * not ported: DraggableSample (its 300 dp black track and red square: the glass action is the moving part here)
 * https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/DraggableSamples.kt#DraggableSample
 *
 * Ported from MTGA:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/MovableAction.kt#MovableAction
 */
@Composable
fun MovableAction(
    icon: ImageVector,
    label: String,
    snapFraction: Float?,
    onSnapFraction: (Float) -> Unit,
    onClick: () -> Unit,
    moveLabel: String = "Move to the other side"
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var container by remember { mutableStateOf(IntSize.Zero) }
    // The live place of the button's top left corner: the DraggableSample's offsetPosition, here on both axes.
    val offsetPosition = remember { mutableStateOf(Offset.Zero) }
    var lifted by remember { mutableStateOf(false) }
    var gliding by remember { mutableStateOf<Job?>(null) }
    val size = with(density) { TopActionSize.roundToPx() }.let { IntSize(it, it) }
    val margin = with(density) { 16.dp.roundToPx() }
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    // The positions the top left of the button may take: 16 dp from every edge, the bars apart.
    val movementBounds = IntRect(
        left = margin,
        top = top + margin,
        right = max(margin, container.width - margin - size.width),
        bottom = max(top + margin, container.height - bottom - margin - size.height)
    )
    val minPx = Offset(movementBounds.left.toFloat(), movementBounds.top.toFloat())
    val maxPx = Offset(movementBounds.right.toFloat(), movementBounds.bottom.toFloat())

    // At rest the place follows the saved fraction and the window; never while a finger or a glide holds it.
    LaunchedEffect(container, movementBounds, snapFraction) {
        if (container == IntSize.Zero || lifted) return@LaunchedEffect
        val at = applySnapFraction(IntRect(IntOffset.Zero, size), movementBounds, snapFraction ?: BOTTOM_RIGHT)
        offsetPosition.value = Offset(at.left.toFloat(), at.top.toFloat())
    }

    fun held() = IntRect(IntOffset(offsetPosition.value.x.roundToInt(), offsetPosition.value.y.roundToInt()), size)

    // Glides to the nearer side, then keeps the fraction once, on landing.
    fun land(to: IntRect) {
        lifted = true
        gliding?.cancel()
        gliding = scope.launch {
            Animatable(offsetPosition.value, Offset.VectorConverter).animateTo(
                Offset(to.left.toFloat(), to.top.toFloat()),
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            ) { offsetPosition.value = value }
            lifted = false
            haptics.tick()
            onSnapFraction(getSnapFraction(to, movementBounds))
        }
    }

    // The lift and its shadow are Animatables moved by animateTo, as the sample's animatedOffset is, here by the
    // lifted flag; both are read in the layer while drawing, never in composition.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
    // not ported: AnimatableAnimateToGenericsType's pointerInput, awaitFirstDown, Offset and Text (the flag is the drag, not a tap)
    val lift = remember { Animatable(1f) }
    val shadow = remember { Animatable(0f) }
    LaunchedEffect(lifted) {
        launch { lift.animateTo(if (lifted) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
        shadow.animateTo(if (lifted) 10f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        if (container != IntSize.Zero) {
            Box(
                Modifier
                    .offset { IntOffset(offsetPosition.value.x.roundToInt(), offsetPosition.value.y.roundToInt()) }
                    .draggable2D(
                        state = rememberDraggable2DState { delta ->
                            if (!lifted) {
                                gliding?.cancel()
                                lifted = true
                                haptics.tick()
                            }
                            val newValue = offsetPosition.value + delta
                            offsetPosition.value = Offset(newValue.x.coerceIn(minPx.x, maxPx.x), newValue.y.coerceIn(minPx.y, maxPx.y))
                        },
                        onDragStopped = { land(snapRectToClosestEdge(held(), movementBounds, STASH_TYPE_NONE)) }
                    )
                    // Lifted: a little larger, a soft shadow.
                    .graphicsLayer {
                        scaleX = lift.value
                        scaleY = lift.value
                        shadowElevation = shadow.value.coerceAtLeast(0f).dp.toPx()
                        shape = CircleShape
                    }
                    .semantics(mergeDescendants = true) {
                        customActions = listOf(
                            CustomAccessibilityAction(moveLabel) {
                                val now = held()
                                val other = if (now.left <= (movementBounds.left + movementBounds.right) / 2) STASH_TYPE_RIGHT else STASH_TYPE_LEFT
                                land(snapRectToClosestEdge(now, movementBounds, other))
                                true
                            }
                        )
                    }
            ) {
                FloatingAction(icon = icon, label = label, onClick = onClick)
            }
        }
    }
}

private const val STASH_TYPE_NONE = 0
private const val STASH_TYPE_LEFT = 1
private const val STASH_TYPE_RIGHT = 2

// Bottom right: the right edge, at its lowest (a fraction of 2 on the clockwise ring below).
private const val BOTTOM_RIGHT = 2f

/**
 * Snaps the stackBounds to the closest side of the movementBounds and returns the new bounds (boundsOut). Only
 * the left and right sides are kept: the top and bottom edges of the original are left out, the height stays.
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/libs/WindowManager/Shell/src/com/android/wm/shell/common/pip/PipSnapAlgorithm.java#snapRectToClosestEdge
 * Ported from MTGA:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/MovableAction.kt#snapRectToClosestEdge
 */
internal fun snapRectToClosestEdge(stackBounds: IntRect, movementBounds: IntRect, stashType: Int): IntRect {
    var leftEdge = stackBounds.left
    if (stashType == STASH_TYPE_LEFT) {
        leftEdge = movementBounds.left
    } else if (stashType == STASH_TYPE_RIGHT) {
        leftEdge = movementBounds.right
    }
    val boundedLeft = max(movementBounds.left, min(movementBounds.right, leftEdge))
    val boundedTop = max(movementBounds.top, min(movementBounds.bottom, stackBounds.top))
    var boundsOut = stackBounds

    // Otherwise, just find the closest side
    val fromLeft = abs(leftEdge - movementBounds.left)
    val fromRight = abs(movementBounds.right - leftEdge)
    val shortest = min(fromLeft, fromRight)
    boundsOut = if (shortest == fromLeft) {
        IntRect(IntOffset(movementBounds.left, boundedTop), stackBounds.size)
    } else {
        IntRect(IntOffset(movementBounds.right, boundedTop), stackBounds.size)
    }
    return boundsOut
}

/**
 * Returns a fraction that describes where along the movementBounds the stackBounds are, clockwise from the
 * top left: 0 to 1 the top side, 1 to 2 the right side, 2 to 3 the bottom, 3 to 4 the left side.
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/libs/WindowManager/Shell/src/com/android/wm/shell/common/pip/PipSnapAlgorithm.java#getSnapFraction
 * Ported from MTGA:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/MovableAction.kt#getSnapFraction
 */
internal fun getSnapFraction(stackBounds: IntRect, movementBounds: IntRect, stashType: Int = STASH_TYPE_NONE): Float {
    val tmpBounds = snapRectToClosestEdge(stackBounds, movementBounds, stashType)
    val widthFraction = (tmpBounds.left - movementBounds.left).toFloat() / movementBounds.width
    val heightFraction = (tmpBounds.top - movementBounds.top).toFloat() / movementBounds.height
    return if (tmpBounds.top == movementBounds.top) {
        widthFraction
    } else if (tmpBounds.left == movementBounds.right) {
        1f + heightFraction
    } else if (tmpBounds.top == movementBounds.bottom) {
        2f + (1f - widthFraction)
    } else {
        3f + (1f - heightFraction)
    }
}

/**
 * Moves the stackBounds along the movementBounds to the given snap fraction (see [getSnapFraction]).
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/libs/WindowManager/Shell/src/com/android/wm/shell/common/pip/PipSnapAlgorithm.java#applySnapFraction
 * Ported from MTGA:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/MovableAction.kt#applySnapFraction
 */
internal fun applySnapFraction(stackBounds: IntRect, movementBounds: IntRect, snapFraction: Float): IntRect {
    var fraction = snapFraction
    val at = if (fraction < 1f) {
        IntOffset(movementBounds.left + (fraction * movementBounds.width).toInt(), movementBounds.top)
    } else if (fraction < 2f) {
        fraction -= 1f
        IntOffset(movementBounds.right, movementBounds.top + (fraction * movementBounds.height).toInt())
    } else if (fraction < 3f) {
        fraction -= 2f
        IntOffset(movementBounds.left + ((1f - fraction) * movementBounds.width).toInt(), movementBounds.bottom)
    } else {
        fraction -= 3f
        IntOffset(movementBounds.left, movementBounds.top + ((1f - fraction) * movementBounds.height).toInt())
    }
    return IntRect(at, stackBounds.size)
}
