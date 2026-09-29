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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LinkedOut's loading mark, the launcher icon in motion: its "out" writes
 * itself by hand, the o, the u, the t and its bar, one after the other the
 * way a pen would, rests a moment, fades, and starts again. The same idea as
 * the handwritten hello that greets a new iPhone.
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
    val ink = MaterialTheme.colorScheme.primary
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
        val scale = this.size.minDimension * FILL / WORD_WIDTH
        val measure = PathMeasure()
        translate(this.size.width / 2f - WORD_CENTRE.x, this.size.height / 2f - WORD_CENTRE.y) {
            scale(scale, pivot = WORD_CENTRE) {
                strokes.forEachIndexed { i, stroke ->
                    val (from, to) = SPANS[i]
                    val done = ease(((time - from) / (to - from)).coerceIn(0f, 1f))
                    if (done <= 0f) return@forEachIndexed
                    val part = Path()
                    measure.setPath(stroke, false)
                    measure.getSegment(0f, lengths[i] * done, part, true)
                    drawPath(
                        part,
                        ink.copy(alpha = fade),
                        style = Stroke(width = PEN, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

private fun ease(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f

// The strokes of the launcher icon, in the order a hand writes them, drawn
// on its 512 grid.
private val STROKES = listOf(
    "M60,279 a56,56 0 1,0 112,0 a56,56 0 1,0 -112,0",
    "M224,223 V286 A49,49 0 0,0 322,286 V223",
    "M414,177 V298 A37,37 0 0,0 451,335",
    "M378,223 H448"
)

/** When each stroke is written, as parts of the loop. */
private val SPANS = listOf(0f to 0.24f, 0.24f to 0.44f, 0.44f to 0.58f, 0.58f to 0.66f)

private const val WRITTEN = 0.66f
private const val FADE_FROM = 0.9f
private const val PEN = 34f
private val WORD_CENTRE = Offset(255.5f, 256f)
private const val WORD_WIDTH = 425f
private const val FILL = 0.9f
private const val LOOP_MILLIS = 2600
