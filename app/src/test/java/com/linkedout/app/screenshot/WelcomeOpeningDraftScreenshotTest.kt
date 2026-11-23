package com.linkedout.app.screenshot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.R
import com.linkedout.app.feature.settings.SettingsIcons
import com.linkedout.app.ui.component.SetupTile
import com.linkedout.app.ui.component.SetupTileGrid
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 27, MTGA's point 26), nothing of app/src/main changes: the app's opening
 * screen. The guide's block (mark, one line, the four tiles, Start) centred in the window's height, Start as an 80 dp
 * cookie in the phone's primary that morphs to a circle when pressed, 40 dp under the tiles, nothing under it, no
 * "Still needed" line, every refusal on its own tile (error flag). Frames: rest, pressed (progress 1, scale .86, a
 * quarter turn, the arrow upright), refused.
 * Layout ported from https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 * through MTGA's
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeGuideDraftScreenshotTest.kt#WelcomeGuideDraftScreenshotTest
 * and
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#WelcomeCookieStartDraftScreenshotTest
 * The cookie's clip is Android's own recipe
 * https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/ShapesSnippets.kt#MorphPolygonShape
 * The tiles are those of
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/WelcomeScreenshotTest.kt#firstRun
 *
 * LoadingMark does not draw on Robolectric (its modulated bitmaps), so [MarkStandIn] shows the mark's two bitmaps as
 * plain images at 160 dp, as WelcomeModelDraftScreenshotTest does: it shows where the running mark sits.
 * not ported: WhatsNewView's footer band, secondary action sheet, version store, title and feature helper views (the
 * app's line and SetupTileGrid take their place); MTGA's WelcomeGuideDraft pinned footer and its 88 dp bottom reserve
 * (Start ends the block here); MTGA's MorphShape and CookieStartDraft, which cite Voice's MorphShape.kt and
 * PlayButton.kt (GPL-3.0, this app is MIT: MorphPolygonShape and a plain clickable Box take their place, no haptic in
 * a picture); MTGA's springs, pressed state and turns counter; MTGA's Sources tile (LinkedOut has no source list).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class WelcomeOpeningDraftScreenshotTest : ScreenshotBase() {

    private fun tile(
        id: String, name: String, icon: ImageVector, state: String,
        done: Boolean, error: Boolean = false, onClick: () -> Unit = {}
    ) = SetupTile(
        id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
        required = false, error = error, onClick = onClick
    )

    /** The first run as WelcomeScreenshotTest#firstRun draws it, nothing refused. */
    private val tilesRest = listOf(
        tile("media", "Save media", LinkedOutIcons.Download, "Never", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", done = true),
        tile("notify", "New posts", LinkedOutIcons.Bell, "Off", done = true),
        tile("links", "LinkedIn links", SettingsIcons.Link, "Off", done = false)
    )

    /**
     * What the phone refused: the refusal is the tile's error colour and its state, no message line. A saved media
     * with no progress bar is a refusal too (MTGA's tile: error when the choice is on, notifications are blocked and the
     * media is refused). The blocked New posts tile's tap opens Android's notification
     * page of the app (nothing runs in a picture: the tap is empty here).
     */
    private fun tilesRefused(notifyState: String) = listOf(
        tile("media", "Save media", LinkedOutIcons.Download, "Saved, no progress bar", done = true, error = true),
        tile("updates", "Updates", SettingsIcons.Update, "Installs not allowed", done = false, error = true),
        tile("notify", "New posts", LinkedOutIcons.Bell, notifyState, done = false, error = true),
        tile("links", "LinkedIn links", SettingsIcons.Link, "Off", done = false)
    )

    @Test
    fun rest() = capture("welcome_opening_draft") { WelcomeOpeningDraft(tilesRest, onStart = {}) }

    @Test
    fun pressed() = capture("welcome_opening_pressed_draft") {
        WelcomeOpeningDraft(tilesRest, onStart = {}, morphProgress = 1f, scale = 0.86f, rotation = 90f)
    }

    @Test
    fun refused() = capture("welcome_opening_refused_draft") {
        WelcomeOpeningDraft(tilesRefused("Notifications blocked"), onStart = {})
    }

    // The variant of New posts' refusal: the notifications are allowed but the system may delay the check.
    @Test
    fun refusedLate() = capture("welcome_opening_refused_late_draft") {
        WelcomeOpeningDraft(tilesRefused("Checks may be late"), onStart = {})
    }

    // Edge: the rest frame at 200 % text and on a 360 dp phone (ScreenshotBase#captureEdge).
    @Test
    fun edge() = captureEdge("welcome_opening_draft") { WelcomeOpeningDraft(tilesRest, onStart = {}) }
}

/**
 * FabMediumTokens: ContainerHeight 80 dp, IconSize 28 dp (the container is as wide as it is high).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FabMediumTokens.kt#FabMediumTokens
 * not ported: ContainerShape (the cookie here), ContainerWidth, container colours, focus, hover, pressed and icon
 * colours, elevations (a draft).
 */
private val FabMediumContainerSize: Dp = 80.dp
private val FabMediumIconSize: Dp = 28.dp

/**
 * arrow_forward, Material Symbols Rounded 24px: viewport 960, mirrored in right-to-left (autoMirror) as the app's icons
 * are.
 * https://github.com/google/material-design-icons/blob/master/symbols/android/arrow_forward/materialsymbolsrounded/arrow_forward_24px.xml#arrow_forward
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
 * Material 3 Expressive's Cookie9Sided: a nine pointed star, inner radius .8, rounding .5, turned -90 degrees. Left
 * at radius 1 around (0, 0), not normalized, which is what [MorphPolygonShape] assumes. Built from graphics-shapes
 * (material3 1.4.0 has no MaterialShapes yet).
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
 * Start: an 80 dp cookie that morphs to the circle at `morphProgress`, shrinks to `scale` and turns by `rotation`, the
 * arrow turning back against it so it stays upright. Fixed values here (the picture is still). A clickable Box with a
 * ripple, not a Material control.
 * Idea of the press (sink, quarter turn, circle) from MTGA's CookieStartDraft in
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/WelcomeCookieStartDraftScreenshotTest.kt#CookieStartDraft
 * not ported: the animations (springs, pressed state, turns counter), the haptic, the size parameter
 * (FabMediumContainerSize).
 */
@Composable
private fun CookieStartDraft(
    onClick: () -> Unit,
    description: String,
    morphProgress: Float,
    scale: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val morph = remember { Morph(Cookie9Sided, CircleShape) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(FabMediumContainerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .clip(MorphPolygonShape(morph, morphProgress.coerceIn(0f, 1f)))
            .background(colors.primary)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberVectorPainter(ArrowForward),
            contentDescription = description,
            colorFilter = ColorFilter.tint(colors.onPrimary),
            modifier = Modifier.size(FabMediumIconSize).graphicsLayer { rotationZ = -rotation }
        )
    }
}

@Composable
private fun MarkStandIn() {
    val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
    val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
    Box(Modifier.size(160.dp)) {
        Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/**
 * WhatsNewView's body without the pinned footer: the button ends the content block, which is centred when shorter
 * than the screen, with no bottom reserve.
 * https://github.com/SvenTiigi/WhatsNewKit/blob/main/Sources/View/WhatsNewView.swift#body
 */
@Composable
private fun WelcomeOpeningDraft(
    tiles: List<SetupTile>,
    onStart: () -> Unit,
    morphProgress: Float = 0f,
    scale: Float = 1f,
    rotation: Float = 0f
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                MarkStandIn()
                Spacer(Modifier.height(16.dp))
                Text(
                    "LinkedIn reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(tiles) {}
                Spacer(Modifier.height(40.dp))
                CookieStartDraft(
                    onClick = onStart, description = "Start",
                    morphProgress = morphProgress, scale = scale, rotation = rotation
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}
