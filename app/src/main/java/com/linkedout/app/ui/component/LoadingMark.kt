package com.linkedout.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
) {
    val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
    val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
    val scheme = MaterialTheme.colorScheme
    // A light tone of the accent on either page, as on the icon.
    val ink = if (scheme.background.luminance() < 0.5f) scheme.primary else scheme.inversePrimary
    val tint = remember(ink) { ColorFilter.tint(ink, BlendMode.Modulate) }
    val strokes = remember { STROKES.map { PathParser().parsePathString(it).toPath() } }
    val lengths = remember { strokes.map { PathMeasure().apply { setPath(it, false) }.length } }

    val transition = rememberInfiniteTransition(label = "written out")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "pen"
    )
    // Writing takes the first two thirds of the loop, then the word rests
    // and fades. A gesture only writes, it never fades.
    val time = if (running) clock else progress * WRITTEN
    val fade = if (running && time > FADE_FROM) 1f - (time - FADE_FROM) / (1f - FADE_FROM) else 1f

    Canvas(modifier.size(size)) {
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
