package com.linkedout.app.ui.component

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * A screen that leaves outward from a tapped point, with Android SystemUI's own sparkle ripple (the one of the Pixel's
 * unlock and charging). The layer to remove gets a hole that grows with the first ripple, the ripple is drawn above.
 *
 * Ported, Apache-2.0 (LineageOS lineage-23.0, packages/SystemUI/animation/src/com/android/systemui/surfaceeffects):
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleShader
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/ShaderUtilLibrary.kt#ShaderUtilLibrary
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/SdfShaderLibrary.kt#SdfShaderLibrary
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleView.kt#RippleView
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimation.kt#RippleAnimation
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimationConfig.kt#RippleAnimationConfig
 *
 * RippleView is a View, here it is split: its duration is RIPPLE_DURATION, its setMaxSize and setCenter are in
 * RippleLayer and RippleAnimation's applyConfigToShader, its onDraw circle branch is RippleLayer's drawBehind.
 * not ported: rippleShape (only the circle is used)
 * not ported: buildShader (RippleAnimation builds its RippleShader)
 * not ported: setupShader (RippleAnimation builds its RippleShader)
 * not ported: onAttachedToWindow (no View, nothing to rebuild)
 * not ported: onConfigurationChanged (no View, the size comes from the layer)
 * not ported: onDraw (its circle branch is RippleLayer's drawBehind, the ellipse and rounded box branches are unused)
 * not ported: onAnimationEnd (the animator listener, StartRevealState.startRipple calls its callback and RippleAnimation.play has its own)
 * not ported: startRipple's ValueAnimator (StartRevealState.startRipple runs a frame loop and RippleAnimation.play)
 * not ported: rippleInProgress (RippleAnimation.isPlaying says it)
 * not ported: baseRingFadeParams (RippleView's setter, RippleAnimationConfig carries them, null here)
 * not ported: sparkleRingFadeParams (RippleView's setter, RippleAnimationConfig carries them, null here)
 * not ported: centerFillFadeParams (RippleView's setter, RippleAnimationConfig carries them, null here)
 * not ported: FadeParams (RippleView's setters, the defaults stay)
 * not ported: blur (RippleView's setBlur, the defaults stay)
 * not ported: color (RippleView's setColor, the config holds it)
 * not ported: sparkleStrength (RippleView's setSparkleStrength, the config holds it)
 * Also left out: MultiRippleView (reduced to the list of playing ripples), the ROUNDED_BOX and ELLIPSE shaders and
 * their SdfShaderLibrary parts, RippleView's ripplePaint and animator (Compose draws, the state's frame loop is the clock).
 * Our choice, not SystemUI's: the two waves start 150 ms and 300 ms after the first ripple.
 */
/**
 * The ripple above both layers, MultiRippleView's place in the tree. It takes the touches of the whole window while it
 * is there, as Surface does with its pointerInput(Unit) {}. On API 31 and 32 it draws nothing, the hole alone makes the
 * reveal.
 * https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Surface.kt#Surface
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleView.kt#MultiRippleView
 * not ported: Surface's color, shape, border and elevation (nothing is drawn by the box itself)
 * not ported: onDraw (it is RippleLayer's drawBehind, below)
 */
@Composable
fun StartRevealOverlay(startRevealState: StartRevealState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().pointerInput(Unit) {}) {
        if (Build.VERSION.SDK_INT >= 33) RippleLayer(startRevealState)
    }
}

// RippleView's setCenter, setMaxSize (twice the distance to the farthest corner), duration, color and pixelDensity,
// then onDraw's circle branch and MultiRippleView's onDraw loop over its ripples, each drawn with its rippleShader.
// not ported: onDraw's hardware acceleration check, ELLIPSE and rounded box branches and ripplePaint (Compose draws on
// the GPU, only the circle is used, the shader is the brush)
// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleView.kt#onDraw
// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleView.kt#onDraw
@RequiresApi(33)
@Composable
private fun RippleLayer(startRevealState: StartRevealState) {
    var maxSize by remember { mutableStateOf(IntSize.Zero) }
    val color = MaterialTheme.colorScheme.primary
    val pixelDensity = LocalDensity.current.density
    val center = startRevealState.origin
    val multiRippleWaves = remember(center, maxSize, color, pixelDensity) {
        if (maxSize == IntSize.Zero || center == Offset.Unspecified) null else {
            val farthest = hypot(max(center.x, maxSize.width - center.x), max(center.y, maxSize.height - center.y))
            Waves(
                RippleAnimationConfig(
                    duration = RIPPLE_DURATION.toLong(),
                    centerX = center.x,
                    centerY = center.y,
                    maxWidth = farthest * 2f,
                    maxHeight = farthest * 2f,
                    pixelDensity = pixelDensity,
                    color = color.toArgb()
                )
            )
        }
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { maxSize = it }.drawBehind {
            val multipleRippleView = multiRippleWaves
                ?.also { it.update(startRevealState.currentPlayTime) }?.multipleRippleView
                ?: return@drawBehind
            multipleRippleView.ripples.toList().forEach { anim ->
                drawCircle(
                    brush = ShaderBrush(anim.rippleShader),
                    radius = anim.rippleShader.rippleSize.currentWidth,
                    center = center
                )
            }
        }
    )
}

/**
 * For the layer that leaves: it is drawn off screen and a circle is cleared out of it, centred on the origin, as wide as
 * the first ripple, so what is under it shows through. Apply it only while the reveal runs.
 * Avatar's drawWithContent that draws a Color.Black circle with BlendMode.Clear over its content, in the snippets'
 * ModifierGraphicsLayerCompositingStrategy layer drawn Offscreen:
 * https://raw.githubusercontent.com/android/snippets/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/GraphicsModifiersSnippets.kt#Avatar
 * not ported: Avatar's Stroke, strokeWidth and clip(CircleShape) (the hole is a filled circle, nothing is clipped)
 */
fun Modifier.startRevealHole(startRevealState: StartRevealState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val center = startRevealState.origin
        if (center == Offset.Unspecified) return@drawWithContent
        val radius = currentWidthRadius(center.x, center.y, size.width, size.height, startRevealState.currentPlayTime)
        if (radius > 0f) {
            drawCircle(
                Color.Black,
                radius,
                center,
                blendMode = BlendMode.Clear
            )
        }
    }

/** RippleView's duration, in milliseconds. */
private const val RIPPLE_DURATION = 1750

// Our choice: the two waves start 150 ms and 300 ms after the first ripple.
private val WAVE_DELAYS = listOf(0, 150, 300)
private const val WAVE_TOTAL = RIPPLE_DURATION + 300

// Copied from RippleShader's companion (Interpolators#STANDARD). At file level so the hole needs no shader on API 31 and 32.
private val STANDARD: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

/**
 * RippleView's startRipple as a state: where the ripple is centred (root coordinates, set before [startRipple]) and the
 * play time in milliseconds. Remember it in the screen that owns the layer.
 * not ported: onAnimationEnd (the animator's listener object, the parameter is called at the end)
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleView.kt#startRipple
 */
@Stable
class StartRevealState {
    var origin by mutableStateOf(Offset.Unspecified)
    var currentPlayTime by mutableFloatStateOf(0f)

    /**
     * Plays the ripple, then calls [onAnimationEnd]. The waves end 300 ms after the first ripple. API 31 and 32 have no
     * shader, only the hole, which is done at 1750.
     */
    suspend fun startRipple(onAnimationEnd: () -> Unit) {
        val duration = if (Build.VERSION.SDK_INT >= 33) WAVE_TOTAL else RIPPLE_DURATION
        val animator = withFrameMillis { it }
        currentPlayTime = 0f
        while (currentPlayTime < duration) {
            currentPlayTime = min(withFrameMillis { it } - animator, duration.toLong()).toFloat()
        }
        onAnimationEnd()
    }
}

/**
 * The first ripple's circle radius at [now] ms: RippleAnimation's AccelerateDecelerateInterpolator into RippleShader's
 * STANDARD into RippleSize's currentWidth (twice the farthest corner), half of it as the shader's radius.
 * https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleSize
 */
private fun currentWidthRadius(centerX: Float, centerY: Float, maxWidth: Float, maxHeight: Float, now: Float): Float {
    val farthest = hypot(max(centerX, maxWidth - centerX), max(centerY, maxHeight - centerY))
    val progress = AccelerateDecelerateInterpolator().getInterpolation(min(now, RIPPLE_DURATION.toFloat()) / RIPPLE_DURATION)
    return farthest * STANDARD.getInterpolation(progress)
}

// MultiRippleView without the View: the list of the playing ripples, and its onDraw's shouldInvalidate.
// not ported: onDraw (RippleLayer's drawBehind draws each ripple as a circle)
// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleView.kt#MultiRippleView
@RequiresApi(33)
private class MultiRippleView {
    val ripples = ArrayList<RippleAnimation>()

    fun invalidate() {}

    fun shouldInvalidate(): Boolean {
        var shouldInvalidate = false
        ripples.forEach { anim ->
            shouldInvalidate = shouldInvalidate || anim.isPlaying()
        }
        return shouldInvalidate
    }
}

// MultiRippleController plays the three RippleAnimations on one MultiRippleView, the state's clock steps them.
// not ported: play (MultiRippleController does it, update calls it)
// not ported: updateColor (the colour is fixed for the reveal, MultiRippleController has it)
// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
@RequiresApi(33)
private class Waves(config: RippleAnimationConfig) {
    val multipleRippleView = MultiRippleView()
    private val multiRippleController = MultiRippleController(multipleRippleView)
    private val rippleAnimations = WAVE_DELAYS.map { RippleAnimation(config) }
    private val rippleStarted = BooleanArray(rippleAnimations.size)

    /** [now]: milliseconds since Start, RippleAnimation's currentPlayTime plus the wave's delay. */
    fun update(now: Float) {
        rippleAnimations.forEachIndexed { i, rippleAnimation ->
            val playTime = (now - WAVE_DELAYS[i]).toLong()
            if (playTime >= 0 && !rippleStarted[i]) {
                rippleStarted[i] = true
                multiRippleController.play(rippleAnimation)
            }
            if (rippleStarted[i]) rippleAnimation.update(min(playTime, RIPPLE_DURATION.toLong()))
        }
    }
}

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
@RequiresApi(33)
private class MultiRippleController(private val multipleRippleView: MultiRippleView) {

    companion object {
        const val MAX_RIPPLE_NUMBER = 10
    }

    fun updateColor(color: Int) {
        multipleRippleView.ripples.forEach { anim -> anim.updateColor(color) }
    }

    fun play(rippleAnimation: RippleAnimation) {
        if (multipleRippleView.ripples.size >= MAX_RIPPLE_NUMBER) {
            return
        }

        multipleRippleView.ripples.add(rippleAnimation)

        rippleAnimation.play {
            // Remove ripple once the animation is done
            multipleRippleView.ripples.remove(rippleAnimation)
        }

        // Trigger drawing
        multipleRippleView.invalidate()
    }
}

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimationConfig.kt#RippleAnimationConfig
@RequiresApi(33)
private data class RippleAnimationConfig(
    val duration: Long = 0L,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val maxWidth: Float = 0f,
    val maxHeight: Float = 0f,
    val pixelDensity: Float = 1f,
    var color: Int = android.graphics.Color.WHITE,
    val opacity: Int = RippleShader.RIPPLE_DEFAULT_ALPHA,
    val sparkleStrength: Float = RippleShader.RIPPLE_SPARKLE_STRENGTH,
    // Null means it uses default fade parameter values.
    val baseRingFadeParams: RippleShader.FadeParams? = null,
    val sparkleRingFadeParams: RippleShader.FadeParams? = null,
    val centerFillFadeParams: RippleShader.FadeParams? = null,
    val shouldDistort: Boolean = true
)

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleAnimation.kt#RippleAnimation
// not ported: onAnimationEnd (the animator's listener, update calls the callback when the play time reaches the duration)
// not ported: animator (the frame loop of StartRevealState gives the play time to update)
@RequiresApi(33)
private class RippleAnimation(private val config: RippleAnimationConfig) {
    val rippleShader: RippleShader = RippleShader()
    private var playing = false
    private var onAnimationEnd: Runnable? = null

    init {
        applyConfigToShader()
    }

    fun updateColor(color: Int) {
        config.apply { config.color = color }
        applyConfigToShader()
    }

    fun play(onAnimationEnd: Runnable? = null) {
        if (isPlaying()) {
            return // Ignore if ripple effect is already playing
        }
        this.onAnimationEnd = onAnimationEnd
        playing = true
    }

    fun isPlaying(): Boolean = playing

    // The update listener of RippleAnimation.play: currentPlayTime in ms, animatedValue from ValueAnimator's default
    // AccelerateDecelerateInterpolator over the duration.
    fun update(now: Long) {
        if (!playing) return
        val progress = AccelerateDecelerateInterpolator().getInterpolation(now.toFloat() / config.duration)
        rippleShader.rawProgress = progress
        rippleShader.distortionStrength = if (config.shouldDistort) 1 - progress else 0f
        rippleShader.time = now.toFloat()
        if (now >= config.duration) {
            playing = false
            onAnimationEnd?.run()
        }
    }

    private fun applyConfigToShader() {
        with(rippleShader) {
            setCenter(config.centerX, config.centerY)
            rippleSize.setMaxSize(config.maxWidth, config.maxHeight)
            pixelDensity = config.pixelDensity
            // ColorUtils.setAlphaComponent(config.color, config.opacity)
            color = (config.color and 0x00ffffff) or (config.opacity shl 24)
            sparkleStrength = config.sparkleStrength

            assignFadeParams(baseRingFadeParams, config.baseRingFadeParams)
            assignFadeParams(sparkleRingFadeParams, config.sparkleRingFadeParams)
            assignFadeParams(centerFillFadeParams, config.centerFillFadeParams)
        }
    }

    private fun assignFadeParams(
        destFadeParams: RippleShader.FadeParams,
        srcFadeParams: RippleShader.FadeParams?
    ) {
        srcFadeParams?.let {
            destFadeParams.fadeInStart = it.fadeInStart
            destFadeParams.fadeInEnd = it.fadeInEnd
            destFadeParams.fadeOutStart = it.fadeOutStart
            destFadeParams.fadeOutEnd = it.fadeOutEnd
        }
    }
}

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/ShaderUtilLibrary.kt#ShaderUtilLibrary
private object ShaderUtilLibrary {
    // language=AGSL
    const val SHADER_LIB =
        """
        float triangleNoise(vec2 n) {
            n  = fract(n * vec2(5.3987, 5.4421));
            n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
            float xy = n.x * n.y;
            // compute in [0..2[ and remap to [-1.0..1.0[
            return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
        }

        const float PI = 3.1415926535897932384626;

        float sparkles(vec2 uv, float t) {
            float n = triangleNoise(uv);
            float s = 0.0;
            for (float i = 0; i < 4; i += 1) {
                float l = i * 0.01;
                float h = l + 0.1;
                float o = smoothstep(n - l, h, n);
                o *= abs(sin(PI * o * (t + 0.55 * i)));
                s += o;
            }
            return s;
        }

        vec2 distort(vec2 p, float time, float distort_amount_radial,
            float distort_amount_xy) {
                float angle = atan(p.y, p.x);
                  return p + vec2(sin(angle * 8 + time * 0.003 + 1.641),
                            cos(angle * 5 + 2.14 + time * 0.00412)) * distort_amount_radial
                     + vec2(sin(p.x * 0.01 + time * 0.00215 + 0.8123),
                            cos(p.y * 0.01 + time * 0.005931)) * distort_amount_xy;
        }

        // Perceived luminosity (L′), not absolute luminosity.
        half getLuminosity(vec3 c) {
            return 0.3 * c.r + 0.59 * c.g + 0.11 * c.b;
        }

        // Creates a luminosity mask and clamp to the legal range.
        vec3 maskLuminosity(vec3 dest, float lum) {
            dest.rgb *= vec3(lum);
            // Clip back into the legal range
            dest = clamp(dest, vec3(0.), vec3(1.0));
            return dest;
        }

        // Integer mod. GLSL es 1.0 doesn't have integer mod :(
        int imod(int a, int b) {
            return a - (b * (a / b));
        }

        ivec3 imod(ivec3 a, int b) {
            return ivec3(imod(a.x, b), imod(a.y, b), imod(a.z, b));
        }

        // Integer based hash function with the return range of [-1, 1].
        vec3 hash(vec3 p) {
            ivec3 v = ivec3(p);
            v = v * 1671731 + 10139267;

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            ivec3 v2 = v / 65536; // v >> 16
            v = imod((10 - imod((v + v2), 10)), 10); // v ^ v2

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            // Use sin and cos to map the range to [-1, 1].
            return vec3(sin(float(v.x)), cos(float(v.y)), sin(float(v.z)));
        }

        // Skew factors (non-uniform).
        const half SKEW = 0.3333333;  // 1/3
        const half UNSKEW = 0.1666667;  // 1/6

        // Return range roughly [-1,1].
        half simplex3d(vec3 p) {
            // Skew the input coordinate, so that we get squashed cubical grid
            vec3 s = floor(p + (p.x + p.y + p.z) * SKEW);

            // Unskew back
            vec3 u = s - (s.x + s.y + s.z) * UNSKEW;

            // Unskewed coordinate that is relative to p, to compute the noise contribution
            // based on the distance.
            vec3 c0 = p - u;

            // Encode x0-y0, y0-z0, z0-x0 in a vec3
            vec3 en = c0 - c0.yzx;
            // Each represents whether x0>y0, y0>z0, z0>x0
            en = step(vec3(0.), en);
            // en.zxy encodes z0>x0, x0>y0, y0>x0
            vec3 offset1 = en * (1. - en.zxy); // find max
            vec3 offset2 = 1. - en.zxy * (1. - en); // 1-(find min)
            vec3 offset3 = vec3(1.);

            vec3 c1 = c0 - offset1 + UNSKEW;
            vec3 c2 = c0 - offset2 + UNSKEW * 2.;
            vec3 c3 = c0 - offset3 + UNSKEW * 3.;

            // Kernel summation: dot(max(0, r^2-d^2))^4, noise contribution)
            vec4 w; // w = max(0, r^2 - d^2))
            w.x = dot(c0, c0);
            w.y = dot(c1, c1);
            w.z = dot(c2, c2);
            w.w = dot(c3, c3);

            w = max(0.6 - w, 0.);

            // Noise contribution from each point.
            vec4 nc;
            nc.x = dot(hash(s), c0);
            nc.y = dot(hash(s + offset1), c1);
            nc.z = dot(hash(s + offset2), c2);
            nc.w = dot(hash(s + offset3), c3);

            nc *= w*w*w*w;

            // Add all the noise contributions.
            return dot(vec4(32.), nc);
        }

        // Random rotations.
        const mat3 rot1 = mat3(1.0, 0. ,0., 0., 0.15, -0.98, 0., 0.98, 0.15);
        const mat3 rot2 = mat3(-0.95, 0. ,-0.3, 0., 1., 0., 0.3, 0., -0.95);
        const mat3 rot3 = mat3(1.0, 0. ,0., 0., -0.44, -0.89, 0., 0.89, -0.44);

        // Octave = 4
        // Divide each coefficient by 3 to produce more grainy noise.
        half simplex3d_fractal(vec3 p) {
            return 0.675 * simplex3d(p * rot1) + 0.225 * simplex3d(2.0 * p * rot2)
                    + 0.075 * simplex3d(4.0 * p * rot3) + 0.025 * simplex3d(8.0 * p);
        }

        // Screen blend
        vec3 screen(vec3 dest, vec3 src) {
            return dest + src - dest * src;
        }
    """
}

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/SdfShaderLibrary.kt#SdfShaderLibrary
private class SdfShaderLibrary {
    // language=AGSL
    companion object {
        const val CIRCLE_SDF =
            """
            float sdCircle(vec2 p, float r) {
                return (length(p)-r) / r;
            }

            float circleRing(vec2 p, float radius) {
                float thicknessHalf = radius * 0.25;

                float outerCircle = sdCircle(p, radius + thicknessHalf);
                float innerCircle = sdCircle(p, radius);

                return subtract(outerCircle, innerCircle);
            }
        """

        const val SHADER_SDF_OPERATION_LIB =
            """
            float soften(float d, float blur) {
                float blurHalf = blur * 0.5;
                return smoothstep(-blurHalf, blurHalf, d);
            }

            float subtract(float outer, float inner) {
                return max(outer, -inner);
            }
        """
    }
}

// https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleShader
// not ported: buildShader (the circle shader is the one constant CIRCLE_SHADER)
// not ported: rippleShape (only the circle is used)
@RequiresApi(33)
private class RippleShader : RuntimeShader(CIRCLE_SHADER) {

    // language=AGSL
    companion object {
        private val TAG = RippleShader::class.simpleName

        // Default fade in/ out values. The value range is [0,1].
        const val DEFAULT_FADE_IN_START = 0f
        const val DEFAULT_FADE_OUT_END = 1f

        const val DEFAULT_BASE_RING_FADE_IN_END = 0.1f
        const val DEFAULT_BASE_RING_FADE_OUT_START = 0.3f

        const val DEFAULT_SPARKLE_RING_FADE_IN_END = 0.1f
        const val DEFAULT_SPARKLE_RING_FADE_OUT_START = 0.4f

        const val DEFAULT_CENTER_FILL_FADE_IN_END = 0f
        const val DEFAULT_CENTER_FILL_FADE_OUT_START = 0f
        const val DEFAULT_CENTER_FILL_FADE_OUT_END = 0.6f

        const val RIPPLE_SPARKLE_STRENGTH: Float = 0.3f
        const val RIPPLE_DEFAULT_COLOR: Int = 0xffffffff.toInt()
        const val RIPPLE_DEFAULT_ALPHA: Int = 115 // full opacity is 255.

        private const val SHADER_UNIFORMS =
            """
            uniform vec2 in_center;
            uniform vec2 in_size;
            uniform float in_cornerRadius;
            uniform float in_thickness;
            uniform float in_time;
            uniform float in_distort_radial;
            uniform float in_distort_xy;
            uniform float in_fadeSparkle;
            uniform float in_fadeFill;
            uniform float in_fadeRing;
            uniform float in_blur;
            uniform float in_pixelDensity;
            layout(color) uniform vec4 in_color;
            uniform float in_sparkle_strength;
        """

        private const val SHADER_CIRCLE_MAIN =
            """
            vec4 main(vec2 p) {
                vec2 p_distorted = distort(p, in_time, in_distort_radial, in_distort_xy);
                float radius = in_size.x * 0.5;
                float sparkleRing = soften(circleRing(p_distorted-in_center, radius), in_blur);
                float inside = soften(sdCircle(p_distorted-in_center, radius * 1.25), in_blur);
                float sparkle = sparkles(p - mod(p, in_pixelDensity * 0.8), in_time * 0.00175)
                    * (1.-sparkleRing) * in_fadeSparkle;

                float rippleInsideAlpha = (1.-inside) * in_fadeFill;
                float rippleRingAlpha = (1.-sparkleRing) * in_fadeRing;
                float rippleAlpha = max(rippleInsideAlpha, rippleRingAlpha) * in_color.a;
                vec4 ripple = vec4(in_color.rgb, 1.0) * rippleAlpha;
                return mix(ripple, vec4(sparkle), sparkle * in_sparkle_strength);
            }
        """

        private const val CIRCLE_SHADER =
            SHADER_UNIFORMS +
                ShaderUtilLibrary.SHADER_LIB +
                SdfShaderLibrary.SHADER_SDF_OPERATION_LIB +
                SdfShaderLibrary.CIRCLE_SDF +
                SHADER_CIRCLE_MAIN

        private fun subProgress(start: Float, end: Float, progress: Float): Float {
            // Avoid division by 0.
            if (start == end) {
                // If start and end are the same and progress has exceeded the start/ end point,
                // treat it as 1, otherwise 0.
                return if (progress > start) 1f else 0f
            }

            val min = Math.min(start, end)
            val max = Math.max(start, end)
            val sub = Math.min(Math.max(progress, min), max)
            return (sub - start) / (end - start)
        }

        private fun getFade(fadeParams: FadeParams, rawProgress: Float): Float {
            val fadeIn = subProgress(fadeParams.fadeInStart, fadeParams.fadeInEnd, rawProgress)
            val fadeOut =
                1f - subProgress(fadeParams.fadeOutStart, fadeParams.fadeOutEnd, rawProgress)

            return Math.min(fadeIn, fadeOut)
        }

        private fun lerp(start: Float, stop: Float, amount: Float): Float {
            return start + (stop - start) * amount
        }
    }

    fun setCenter(x: Float, y: Float) {
        setFloatUniform("in_center", x, y)
    }

    var blurStart: Float = 1.25f
    var blurEnd: Float = 0.5f

    val rippleSize = RippleSize()

    var rawProgress: Float = 0.0f
        set(value) {
            field = value
            progress = STANDARD.getInterpolation(value)

            setFloatUniform("in_fadeSparkle", getFade(sparkleRingFadeParams, value))
            setFloatUniform("in_fadeRing", getFade(baseRingFadeParams, value))
            setFloatUniform("in_fadeFill", getFade(centerFillFadeParams, value))
        }

    private var progress: Float = 0.0f
        set(value) {
            field = value

            rippleSize.update(value)

            setFloatUniform("in_size", rippleSize.currentWidth, rippleSize.currentHeight)
            setFloatUniform("in_thickness", rippleSize.currentHeight * 0.5f)
            // Corner radius is always max of the min between the current width and height.
            setFloatUniform(
                "in_cornerRadius",
                Math.min(rippleSize.currentWidth, rippleSize.currentHeight)
            )
            setFloatUniform("in_blur", lerp(1.25f, 0.5f, value))
        }

    var time: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_time", value)
        }

    var color: Int = 0xffffff
        set(value) {
            field = value
            setColorUniform("in_color", value)
        }

    var sparkleStrength: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_sparkle_strength", value)
        }

    var distortionStrength: Float = 0.0f
        set(value) {
            field = value
            setFloatUniform("in_distort_radial", 75 * rawProgress * value)
            setFloatUniform("in_distort_xy", 75 * value)
        }

    var pixelDensity: Float = 1.0f
        set(value) {
            field = value
            setFloatUniform("in_pixelDensity", value)
        }

    val sparkleRingFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_SPARKLE_RING_FADE_IN_END,
            DEFAULT_SPARKLE_RING_FADE_OUT_START,
            DEFAULT_FADE_OUT_END
        )

    val baseRingFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_BASE_RING_FADE_IN_END,
            DEFAULT_BASE_RING_FADE_OUT_START,
            DEFAULT_FADE_OUT_END
        )

    val centerFillFadeParams =
        FadeParams(
            DEFAULT_FADE_IN_START,
            DEFAULT_CENTER_FILL_FADE_IN_END,
            DEFAULT_CENTER_FILL_FADE_OUT_START,
            DEFAULT_CENTER_FILL_FADE_OUT_END
        )

    data class FadeParams(
        var fadeInStart: Float = DEFAULT_FADE_IN_START,
        var fadeInEnd: Float,
        var fadeOutStart: Float,
        var fadeOutEnd: Float = DEFAULT_FADE_OUT_END,
    )

    data class SizeAtProgress(
        var t: Float,
        var width: Float,
        var height: Float
    )

    inner class RippleSize {
        var sizes = mutableListOf<SizeAtProgress>()
        var currentSizeIndex = 0
        val initialSize = SizeAtProgress(0f, 0f, 0f)

        var currentWidth: Float = 0f
            private set
        var currentHeight: Float = 0f
            private set

        fun setMaxSize(width: Float, height: Float) {
            setSizeAtProgresses(initialSize, SizeAtProgress(1f, width, height))
        }

        fun setSizeAtProgresses(vararg sizes: SizeAtProgress) {
            // Reset everything.
            this.sizes.clear()
            currentSizeIndex = 0

            this.sizes.addAll(sizes)
            this.sizes.sortBy { it.t }
        }

        fun update(progress: Float) {
            val targetIndex = updateTargetIndex(progress)
            val prevIndex = Math.max(targetIndex - 1, 0)

            val targetSize = sizes[targetIndex]
            val prevSize = sizes[prevIndex]

            val subProgress = subProgress(prevSize.t, targetSize.t, progress)

            currentWidth = targetSize.width * subProgress + prevSize.width
            currentHeight = targetSize.height * subProgress + prevSize.height
        }

        private fun updateTargetIndex(progress: Float): Int {
            if (sizes.isEmpty()) {
                // It could be empty on init.
                if (progress > 0f) {
                    Log.e(
                        TAG,
                        "Did you forget to set the ripple size? Use [setMaxSize] or " +
                            "[setSizeAtProgresses] before playing the animation."
                    )
                }
                // If there's no size is set, we set everything to 0 and return early.
                setSizeAtProgresses(initialSize)
                return currentSizeIndex
            }

            var candidate = sizes[currentSizeIndex]

            while (progress > candidate.t) {
                currentSizeIndex = Math.min(currentSizeIndex + 1, sizes.size - 1)
                candidate = sizes[currentSizeIndex]
            }

            return currentSizeIndex
        }
    }
}
