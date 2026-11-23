package com.linkedout.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.linkedout.app.R
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * LinkedOut's loading mark, the launcher icon in motion: its glossy "out"
 * writes itself by hand, the o, the u, the t and its bar, one after the
 * other the way a pen would, rests a moment, fades, and starts again. The
 * same idea as the handwritten hello that greets a new iPhone.
 *
 * The letters are the icon's: a grey body multiplied by the theme's accent
 * and white highlights over it, uncovered along each stroke as the pen goes.
 *
 * [progress] from 0 to 1 writes the word as far as a gesture has gone. While
 * [running] it writes on its own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) = LoadingMark(modifier, size, running) { progress }

/**
 * The same mark with [progress] read while drawing, so a gesture moving it does not recompose its caller.
 *
 * Ported from MTGA's LoadingMark, which keeps one Animatable clock (its feathers' fall; the pen's loop here):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
 * not ported: FEATHERS, FeatherInk, FeatherArt, drawFeather, FALL_MILLIS (LinkedOut keeps its own handwritten "out",
 * drawn as it was); its gesture clock is a third of a fall, here the written part of the loop
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: () -> Float
) {
    val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
    val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
    val scheme = MaterialTheme.colorScheme
    // A light tone of the accent on either page, as on the icon.
    val ink = if (scheme.background.luminance() < 0.5f) scheme.primary else scheme.inversePrimary
    val tint = remember(ink) { ColorFilter.tint(ink, BlendMode.Modulate) }
    val strokes = remember { STROKES.map { PathParser().parsePathString(it).toPath() } }
    val lengths = remember { strokes.map { PathMeasure().apply { setPath(it, false) }.length } }

    // One time source for both ways: the clock continues from where it stands, and a gesture's progress glides from
    // there, so the pen never jumps when `running` changes. Nothing runs at rest.
    // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/LoadingMark.kt#LoadingMark
    // and the Animatable remembered and moved by animateTo in a coroutine:
    // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
    // not ported: AnimatableAnimateToGenericsType's tap detection and Offset (the target is the loop's time or the pull, not a touch)
    // not ported: LoadingMark's feathers (FEATHERS, FeatherInk, FeatherArt, drawFeather: LinkedOut keeps its own handwritten "out")
    val currentProgress by rememberUpdatedState(progress)
    val clock = remember { Animatable(if (running) 0f else currentProgress() * WRITTEN) }
    LaunchedEffect(running) {
        if (running) {
            // The infinite frame clock, as InfiniteTransition runs on: a test waiting for idle does not wait for it.
            var last = withInfiniteAnimationFrameMillis { it }
            while (true) {
                val now = withInfiniteAnimationFrameMillis { it }
                val next = clock.value + (now - last).toFloat() / LOOP_MILLIS
                clock.snapTo(if (next >= 1f) next - 1f else next)
                last = now
            }
        } else {
            // A gesture only writes: the pen goes as far as the pull, never past the written word.
            snapshotFlow { currentProgress() * WRITTEN }.collectLatest { clock.animateTo(it, tween(90, easing = LinearEasing)) }
        }
    }

    // Writing takes the first two thirds of the loop, then the word rests and fades. A gesture only writes, it
    // never fades. The clock is read while drawing, never in composition.
    Canvas(modifier.size(size)) {
        val time = clock.value
        val fade = if (running && time > FADE_FROM) 1f - (time - FADE_FROM) / (1f - FADE_FROM) else 1f
        val side = this.size.minDimension
        val box = IntSize(side.roundToInt(), side.roundToInt())
        val unit = side / FRAME
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, side, side), Paint().apply { alpha = fade })
            // The pen: each stroke drawn as far as it has gone, as wide as the
            // letters, then the letters kept only where it went.
            val measure = PathMeasure()
            translate(-(FRAME_CENTRE.x - FRAME / 2f) * unit, -(FRAME_CENTRE.y - FRAME / 2f) * unit) {
                scale(unit, pivot = Offset.Zero) {
                    strokes.forEachIndexed { i, stroke ->
                        val (from, to) = SPANS[i]
                        val done = ease(((time - from) / (to - from)).coerceIn(0f, 1f))
                        if (done <= 0f) return@forEachIndexed
                        val part = Path()
                        measure.setPath(stroke, false)
                        measure.getSegment(0f, lengths[i] * done, part, true)
                        drawPath(part, Color.Black, style = Stroke(width = PEN, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            drawImage(body, dstSize = box, colorFilter = tint, blendMode = BlendMode.SrcIn, filterQuality = FilterQuality.Medium)
            drawImage(shine, dstSize = box, blendMode = BlendMode.SrcAtop, filterQuality = FilterQuality.Medium)
            canvas.restore()
        }
    }
}

private fun ease(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f

// The letters' spines, in the order a hand writes them, on the icon's 512
// grid: the o from its top, the u down and up, the t's stem and its bar.
private val STROKES = listOf(
    "M110,226 A54,54 0 1,0 110,334 A54,54 0 1,0 110,226",
    "M236,222 V290 A49,49 0 0,0 334,290 V222",
    "M434,176 V296 A40,40 0 0,0 474,336",
    "M406,228 H474"
)

/** When each stroke is written, as parts of the loop. */
private val SPANS = listOf(0f to 0.24f, 0.24f to 0.44f, 0.44f to 0.58f, 0.58f to 0.66f)

/** The square of the grid the mark's bitmaps hold: the letters' bounds and a margin. */
private val FRAME_CENTRE = Offset(266f, 258f)
private const val FRAME = 508f
/** The pen covers the tube, a little wider so nothing is left at its edge. */
private const val PEN = 62f
private const val WRITTEN = 0.66f
private const val FADE_FROM = 0.9f
private const val LOOP_MILLIS = 2600
