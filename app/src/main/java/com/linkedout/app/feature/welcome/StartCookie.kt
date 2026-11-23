package com.linkedout.app.feature.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.TransformResult
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.linkedout.app.ui.component.rememberHaptics
import kotlinx.coroutines.launch

// Start as a cookie, written from Android's own recipe for a morph as a clip shape (MorphPolygonShape) and the springs of
// Compose's Animatable sample. It is NOT taken from MTGA's StartCookie.kt.
// not ported: StartCookie (MTGA's, derived from Voice, GPL-3.0; LinkedOut is MIT, the cookie is written from Android's MorphPolygonShape)
// not ported: MorphShape (MTGA's, derived from Voice, GPL-3.0; MorphPolygonShape takes its place)
// not ported: AnimatableAnimateToGenericsType (the sample's Offset animation and pointer detection: only animateTo with a spring is used)
// not ported: cookie9 (MaterialShapes' getter: the polygon is built in Cookie9Sided, without the getter's normalization)
// not ported: circle (MaterialShapes' getter: CircleShape is the polygon alone)

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (the cookie here), ContainerWidth, container colours, focus, hover, pressed and icon
 * colours, elevations.
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * Material Symbols Rounded arrow_forward 24px: viewport 960, mirrored in right-to-left (autoMirror) as the app's icons
 * are. The path is the file's.
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml#L1-L11
 * not ported: the tint attribute (the icon takes the colour of its use).
 */
private val ArrowForward: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowForward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M647,520L200,520Q183,520 171.5,508.5Q160,497 160,480Q160,463 171.5,451.5Q183,440 200,440L647,440" +
                    "L451,244Q439,232 439.5,216Q440,200 452,188Q464,177 480,176.5Q496,176 508,188L772,452" +
                    "Q778,458 780.5,465Q783,472 783,480Q783,488 780.5,495Q778,502 772,508L508,772" +
                    "Q497,783 480.5,783Q464,783 452,772Q440,760 440,743.5Q440,727 452,715L647,520Z"
            ).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

/**
 * Material 3 Expressive's Cookie9Sided: a nine pointed star, inner radius .8, rounding .5, turned -90 degrees. Left at
 * radius 1 around (0, 0), not normalized, which is what [MorphPolygonShape] assumes.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#cookie9
 * not ported: the other shapes of MaterialShapes and customPolygon (only the cookie and the circle are drawn), the
 * normalization of the getter (the shape is stretched by MorphPolygonShape instead).
 */
private val Cookie9Sided: RoundedPolygon by lazy {
    RoundedPolygon.star(numVerticesPerRadius = 9, innerRadius = .8f, rounding = CornerRounding(radius = .5f))
        .transformed { x, y -> TransformResult(y, -x) }
}

/**
 * MaterialShapes' circle, ten vertices as its default, radius 1 around (0, 0).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt#circle
 */
private val CircleShape: RoundedPolygon by lazy { RoundedPolygon.circle(numVertices = 10) }

/**
 * A morph between two polygons as a clip shape at a progress.
 * https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/ShapesSnippets.kt#MorphPolygonShape
 * Changed: the matrix is reset at each outline (the snippet's field would add up over redraws).
 */
private class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float
) : Shape {

    private val matrix = Matrix()
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Below assumes that you haven't changed the default radius of 1f, nor the centerX and centerY of 0f
        // By default this stretches the path to the size of the container, if you don't want stretching, use the same size.width for both x and y.
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)

        val path = morph.toPath(progress = percentage).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Start: an 80 dp cookie in the primary colour with the arrow in onPrimary (mirrored in right-to-left). Pressed, the
 * cookie morphs to the circle and sinks to 0.86 on springs; a tap gives a haptic, turns it a quarter (the arrow
 * turning back against it, so it stays upright), then calls [onClick]. [description] is what TalkBack reads. The three
 * values are Animatables read inside graphicsLayer, so a frame never recomposes.
 * The springs: Animatable.animateTo with spring(), as
 * https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
 * The clickable Box with a ripple is no Material control.
 */
@Composable
fun StartCookie(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val morph = remember { Morph(Cookie9Sided, CircleShape) }
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val turn = remember { Animatable(0f) }
    var quarterTurns by remember { mutableIntStateOf(0) }

    // No bounce on the morph: past 1 the polygons would be extrapolated.
    LaunchedEffect(pressed) {
        launch { progress.animateTo(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessMedium)) }
        launch {
            scale.animateTo(
                if (pressed) 0.86f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    LaunchedEffect(quarterTurns) {
        turn.animateTo(
            quarterTurns * 90f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Box(
        modifier = modifier
            .size(FabMediumContainerSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = turn.value
                shape = MorphPolygonShape(morph, progress.value.coerceIn(0f, 1f))
                clip = true
            }
            .background(colors.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClickLabel = description,
                onClick = {
                    haptics.tick()
                    quarterTurns++
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier.size(FabMediumIconSize).graphicsLayer { rotationZ = -turn.value }
        )
    }
}
