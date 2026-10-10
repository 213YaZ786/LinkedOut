package com.linkedout.app.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

/**
 * A zone made of glass over the ambient light: a card, a banner, a section.
 *
 * The zone bends the ambient light behind it near its edges and tints it,
 * then catches the light on its rim. The ambient light is known (see
 * GlassLook), so the bent part is computed right here in a shader from the
 * zone's place in the window, instead of capturing the screen: any number of
 * zones can scroll at no real cost. Below Android 13, which has no runtime
 * shaders, the zone is a tinted pane with the same rim, over the same light.
 */
fun Modifier.glassZone(shape: Shape, look: GlassLook, lens: Float = 0.7f): Modifier =
    this then GlassZoneElement(shape, look, lens)

private data class GlassZoneElement(val shape: Shape, val look: GlassLook, val lens: Float) :
    ModifierNodeElement<GlassZoneNode>() {
    override fun create() = GlassZoneNode(shape, look, lens)
    override fun update(node: GlassZoneNode) {
        node.shape = shape; node.look = look; node.lens = lens; node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "glassZone" }
}

private class GlassZoneNode(var shape: Shape, var look: GlassLook, var lens: Float) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var origin = Offset.Zero
    private var window = Size.Zero
    private var shader: Any? = null
    private var shaderFailed = false

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val o = coordinates.positionInRoot()
        val w = coordinates.findRootCoordinates().size.toSize()
        if (o != origin || w != window) {
            origin = o; window = w; invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val outline = shape.createOutline(size, layoutDirection, this)
        val zone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && window.width > 0f && !shaderFailed) {
            runCatching { zoneShader(outline) }
                .onFailure { shaderFailed = true; android.util.Log.w("Glass", "zone shader failed", it) }
                .getOrNull()
        } else {
            null
        }
        if (zone != null) {
            // A soft shadow under the zone, so it floats over the light (the glass is opaque over it).
            drawIntoCanvas { canvas ->
                val paint = androidx.compose.ui.graphics.Paint().apply { color = look.ground }
                paint.asFrameworkPaint().setShadowLayer(10f * density, 0f, 3.5f * density, android.graphics.Color.argb(if (look.dark) 110 else 34, 0, 0, 0))
                canvas.drawOutline(outline, paint)
            }
            drawOutline(outline, ShaderBrush(zone))
        } else {
            drawOutline(outline, look.zoneTint)
            drawOutline(outline, rimBrush(look, size.height), style = Stroke(width = 1.2f * density))
        }
        drawContent()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.zoneShader(outline: Outline): RuntimeShader? {
        // A shader the phone's engine refuses leaves the plain pane, never a crash.
        val s = (shader as? RuntimeShader) ?: runCatching { RuntimeShader(ZONE_AGSL) }
            .onFailure { shaderFailed = true; android.util.Log.w("Glass", "zone shader refused", it) }
            .getOrNull()?.also { shader = it } ?: return null
        s.setFloatUniform("size", size.width, size.height)
        s.setFloatUniform("radius", cornerRadius(outline))
        s.setFloatUniform("origin", origin.x, origin.y)
        s.setFloatUniform("dpr", density)
        s.setFloatUniform("lens", lens)
        s.setFloatUniform("dark", if (look.dark) 1f else 0f)
        s.setColorUniform("ground", look.ground.toArgb())
        s.setColorUniform("tint", look.zoneTint.toArgb())
        setHalos(s, look, window)
        return s
    }
}

/** The corner radius of a rounded outline, in pixels; a plain rectangle has none. */
internal fun cornerRadius(outline: Outline): Float = when (outline) {
    // The smallest corner: a bubble's tail (one tight corner) is glass too, not left bare.
    is Outline.Rounded -> with(outline.roundRect) { minOf(topLeftCornerRadius.x, topRightCornerRadius.x, bottomLeftCornerRadius.x, bottomRightCornerRadius.x) }
    is Outline.Rectangle -> 0f
    is Outline.Generic -> 0f
}

/** The halos as four plain uniforms each, placed in window pixels; a missing one has no strength. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun setHalos(shader: RuntimeShader, look: GlassLook, window: Size) {
    for (i in 0 until HALOS) {
        val halo = look.halos.getOrNull(i)
        if (halo == null) {
            shader.setFloatUniform("halo$i", 0f, 0f, 1f, 0f)
            shader.setFloatUniform("colour$i", 0f, 0f, 0f, 0f)
        } else {
            shader.setFloatUniform("halo$i", halo.x * window.width, halo.y * window.height, halo.radius * window.width, 0f)
            shader.setFloatUniform("colour$i", halo.color.red, halo.color.green, halo.color.blue, halo.color.alpha)
        }
    }
}

/**
 * The rim without shaders: a soft light on the top edge that fades down
 * the flanks, a faint shade below; never a white line.
 */
internal fun rimBrush(look: GlassLook, height: Float): Brush {
    val strong = Color.White.copy(alpha = if (look.dark) 0.16f else 0.24f)
    val faint = Color.White.copy(alpha = 0.03f)
    return Brush.verticalGradient(0f to strong, 0.35f to faint, 1f to Color.Black.copy(alpha = 0.04f), endY = height)
}

internal const val HALOS = 4

/** Rounded box distance, the rim light and the lens, shared by both shaders. */
internal const val GLASS_COMMON = """
    float sdb(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    float2 normalAt(float2 q, float2 b, float r, float e) {
        float2 n = float2(sdb(q + float2(e, 0.0), b, r) - sdb(q - float2(e, 0.0), b, r),
                          sdb(q + float2(0.0, e), b, r) - sdb(q - float2(0.0, e), b, r));
        return normalize(n + 1e-6);
    }
    // Polished glass (the user's reference, 2026-10-10): a thin rim of light
    // at the very edge, bright at the top and the bottom, faint on the
    // flanks, a hair of shade just inside it for the bevel; under it a wide,
    // gentle gradient, light from above, shade below. Never a flat white line.
    float rimLight(float depth, float2 n, float dpr, float dark) {
        float edge = 1.0 - smoothstep(0.4 * dpr, 1.8 * dpr, depth);
        float bevel = smoothstep(1.2 * dpr, 2.4 * dpr, depth) * (1.0 - smoothstep(2.4 * dpr, 4.5 * dpr, depth));
        float band = 1.0 - smoothstep(0.0, 20.0 * dpr, depth);
        band = band * band * band;
        float vertical = abs(n.y);
        float up = pow(max(-n.y, 0.0), 1.6);
        float down = pow(max(n.y, 0.0), 2.0);
        float rim = edge * (0.10 + 0.32 * pow(vertical, 1.4)) * (dark > 0.5 ? 0.75 : 1.0);
        float light = band * (0.20 + 0.80 * up) * (dark > 0.5 ? 0.10 : 0.10);
        float shade = band * down * (dark > 0.5 ? 0.04 : 0.03) + bevel * (dark > 0.5 ? 0.05 : 0.035);
        return rim + light - shade;
    }
"""

private const val ZONE_AGSL = """
    uniform float2 size;
    uniform float radius;
    uniform float2 origin;
    uniform float dpr;
    uniform float lens;
    uniform float dark;
    layout(color) uniform half4 ground;
    layout(color) uniform half4 tint;
    uniform float4 halo0;
    uniform float4 halo1;
    uniform float4 halo2;
    uniform float4 halo3;
    uniform float4 colour0;
    uniform float4 colour1;
    uniform float4 colour2;
    uniform float4 colour3;
""" + GLASS_COMMON + """
    half3 over(half3 c, float2 w, float4 h, float4 col) {
        float a = col.a * clamp(1.0 - length(w - h.xy) / max(h.z, 1.0), 0.0, 1.0);
        return mix(c, half3(col.rgb), half(a));
    }
    half3 ambient(float2 w) {
        half3 c = ground.rgb;
        c = over(c, w, halo0, colour0);
        c = over(c, w, halo1, colour1);
        c = over(c, w, halo2, colour2);
        c = over(c, w, halo3, colour3);
        return c;
    }
    half4 main(float2 p) {
        float2 b = size * 0.5;
        float2 q = p - b;
        float d = sdb(q, b, radius);
        float2 n = normalAt(q, b, radius, 1.5 * dpr);
        float depth = -d;
        float k = 1.0 - clamp(depth / min(min(b.x, b.y), 22.0 * dpr), 0.0, 1.0);
        k = k * k;
        float2 src = b + q * (1.0 - 0.05 * lens) - n * k * lens * 20.0 * dpr;
        half3 c = ambient(origin + src);
        c = mix(c, tint.rgb, tint.a);
        c += half3(rimLight(depth, n, dpr, dark));
        return half4(c, 1.0);
    }
"""
