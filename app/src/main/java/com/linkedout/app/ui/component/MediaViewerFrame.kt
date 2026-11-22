package com.linkedout.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.Job
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import com.linkedout.app.ui.glass.glassSource
import com.linkedout.app.ui.glass.rememberGlassBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The frame of a media viewer, the same in every reader: a full screen dialog
 * on black (DarkGround), the pages in a pager, a swipe down (or back) to
 * close, and over the pages a row with Close, the counter and the app's own
 * buttons ([actions], given the page in front). A tap on a page hides the
 * row, the player controls and the system bars together; the next tap brings
 * them back (LocalViewerChrome, ViewerChromeEffect).
 *
 * [page] draws one page: a photo as [ZoomableImage], a film or a GIF as the
 * app's player. [onZoomChanged] tells the frame the photo is zoomed, so the
 * pager and the swipe down leave its fingers alone. A page whose content
 * takes no tap (a GIF, a film not started) toggles the chrome through the
 * tap handler of its box; a photo and a started film toggle it themselves.
 *
 * Where it comes from: the viewer of MTGA, Yid, Frontr and LinkedOut (the
 * dialog, pager, drag to dismiss and glass backdrop, moved here from their
 * MediaViewer.kt), and the tap that hides the chrome from ReFra (Apache-2.0,
 * https://github.com/IacobIonut01/ReFra at ba329cb, app/feature/viewer) and
 * Glimpse (Apache-2.0, https://github.com/LineageOS/android_packages_apps_Glimpse).
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaViewerFrame(
    count: Int,
    startIndex: Int,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.(page: Int) -> Unit,
    page: @Composable (index: Int, active: Boolean, onZoomChanged: (Boolean) -> Unit) -> Unit
) {
    if (count <= 0) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) { DarkGround {
        val pager = rememberPagerState(
            initialPage = startIndex.coerceIn(0, count - 1),
            pageCount = { count }
        )
        var zoomed by remember { mutableStateOf(false) }
        // The drag is a plain state: each delta lands in the frame it comes in (a snapTo launched per delta landed
        // a frame late). A swipe away or back is a spring run from it, which a new touch stops.
        // https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType
        var dragY by remember { mutableFloatStateOf(0f) }
        var settle by remember { mutableStateOf<Job?>(null) }
        var screenHeight by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        // Whether the chrome shows: flipped by a tap on a page, read by the row below, by the player
        // controls (LocalViewerChrome) and by the system bars (ViewerChromeEffect).
        val chrome = remember { mutableStateOf(true) }
        ViewerChromeEffect(chrome.value)

        // In glass, the pages are recorded for the round buttons above them
        // to bend what is shown.
        val look = LocalGlass.current
        val pagerBackdrop = rememberGlassBackdrop()

        CompositionLocalProvider(LocalViewerChrome provides chrome) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { screenHeight = it.height }
                    // The black fades with the drag, read in the draw phase: no recomposition per frame.
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = (1f - abs(dragY) / 1200f).coerceIn(0.3f, 1f)))
                    }
            ) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !zoomed,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (look != null) Modifier.glassSource(pagerBackdrop, look) else Modifier)
                        .offset { IntOffset(0, dragY.roundToInt()) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = !zoomed,
                            state = rememberDraggableState { delta -> dragY += delta },
                            onDragStarted = { settle?.cancel() },
                            onDragStopped = { velocity ->
                                settle = scope.launch {
                                    if (abs(dragY) > DISMISS_DISTANCE || abs(velocity) > DISMISS_VELOCITY) {
                                        // Away off the screen the way it was thrown, then closed.
                                        val way = if (dragY != 0f) Math.signum(dragY) else Math.signum(velocity)
                                        val away = way * (if (screenHeight > 0) screenHeight else 2000).toFloat()
                                        animate(dragY, away, velocity, spring(dampingRatio = 1f, stiffness = 400f)) { value, _ ->
                                            dragY = value
                                        }
                                        onDismiss()
                                    } else {
                                        animate(dragY, 0f, velocity, spring()) { value, _ -> dragY = value }
                                    }
                                }
                            }
                        )
                ) { index ->
                    val active = pager.currentPage == index
                    // A tap nothing in the page took flips the chrome. A child that takes the tap (the
                    // photo's, the player's) consumes it, so this one does not fire a second time.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(chrome) {
                                detectTapGestures(onTap = { chrome.value = !chrome.value })
                            }
                    ) {
                        page(index, active) { if (active) zoomed = it }
                    }
                }

                CompositionLocalProvider(LocalGlassBackdrop provides pagerBackdrop.takeIf { look != null }) {
                    AnimatedVisibility(
                        visible = chrome.value,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        // The top row is FloatingTop's layout (SMS, AGPL-3.0, same author,
                        // https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/FloatingPane.kt#FloatingTop):
                        // two slots of the same size around a weight(1f) centre, so the counter stays
                        // centred whatever the actions are.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ViewerActionSize + 16.dp)
                                // The bars may be hidden, so the row keeps the place they have.
                                .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Both slots keep their room, so the counter stays centred.
                            Box(Modifier.size(ViewerActionSize), contentAlignment = Alignment.Center) {
                                FloatingRoundButton(icon = ViewerIcons.Close, label = "Close", onClick = onDismiss)
                            }
                            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (count > 1) "${pager.currentPage + 1} / $count" else "",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                            // The actions take a RowScope, so they sit in a Row inside the trailing slot.
                            Box(Modifier.size(ViewerActionSize), contentAlignment = Alignment.Center) {
                                Row { actions(pager.currentPage) }
                            }
                        }
                    }
                }
            }
        }
    } }
}

/**
 * The size of the round actions at the top, the same 48 dp as FloatingRoundButton. SMS's
 * TopActionSize (https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/FloatingPane.kt#TopActionSize)
 * lives in the panes kit, which LinkedOut, Yid and Frontr do not take, so it is ported here
 * under its own name (the panes kit's one would clash in MTGA, same package).
 */
private val ViewerActionSize = 48.dp

/**
 * Pinch to zoom, pan while zoomed, double tap to toggle, a single tap flips
 * the viewer's chrome (LocalViewerChrome). At rest a single finger is left
 * alone, so the pager and swipe to dismiss still get it. [model] is what
 * Coil loads (an address or a saved file).
 */
@Composable
fun ZoomableImage(model: Any?, onZoomChanged: (Boolean) -> Unit) {
    val chrome = LocalViewerChrome.current
    // The double tap springs between 1x and 2.5x and a pinch stops that spring first: Zoomable does both
    // (https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation-core/samples/src/main/java/androidx/compose/animation/core/samples/AnimatableSamples.kt#AnimatableAnimateToGenericsType).
    Zoomable(onZoomChanged = onZoomChanged, onTap = { chrome?.let { it.value = !it.value } }) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val DISMISS_DISTANCE = 300f
private const val DISMISS_VELOCITY = 2500f

/** Material icons (Apache 2.0), from google/material-design-icons, 24 dp grid. */
private object ViewerIcons {
    val Close by lazy {
        build("M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }

    private fun build(path: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
            .build()
}
