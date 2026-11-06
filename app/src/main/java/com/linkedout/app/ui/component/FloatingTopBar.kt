package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.linkedout.app.navigation.LocalReadableInset
import kotlin.math.roundToInt

/**
 * The top bar as a zone, not as a band.
 *
 * A rounded container with air around it, the same shape and the same margin
 * as a settings section or a post, instead of a slab welded to the edges of
 * the screen. It still folds away as the reader goes down and comes back on
 * the first upward flick.
 *
 * The bar is not given the [scrollBehavior] itself. Material's app bar reacts
 * to one by sliding its own contents, which inside a container would leave an
 * empty rounded box sitting there. The whole zone is moved here instead, its
 * own background included, so what leaves the screen is the zone and not just
 * its text.
 */
/**
 * A screen's title in a top bar: centred, up to two lines, and a single word too wide for the room
 * shrinks last. Ported from SMS's FloatingTop (AGPL-3.0, same author,
 * https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/ui/component/FloatingPane.kt#FloatingTop), which sets
 * a BasicText with maxLines = 2 and Compose's TextAutoSize.StepBased
 * (https://developer.android.com/develop/ui/compose/text/autosize). The size is the bar's own
 * titleLarge, two lines of it fit the 64 dp bar.
 */
@Composable
fun BarTitle(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleLarge
    BasicText(
        text,
        modifier = modifier.semantics { heading() },
        style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit
) {
    val state = scrollBehavior?.state

    Box(
        modifier = modifier
            // Measured with its margins, so the zone clears the screen whole
            // and no sliver of it is left under the status bar.
            .onSizeChanged { size -> state?.heightOffsetLimit = -size.height.toFloat() }
            .offset { IntOffset(0, state?.heightOffset?.roundToInt() ?: 0) }
            // No band behind the zone: what scrolls under it is faded by the
            // list itself (underFloatingTopBar), not cut by an opaque edge.
            .statusBarsPadding()
            // The inset keeps the zone over the readable column on a wide
            // window.
            .padding(horizontal = 16.dp + LocalReadableInset.current, vertical = 8.dp)
    ) {
        ZoneSurface(
            shape = ShapeDefaults.ExtraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            CenterAlignedTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                // The zone above already sits below the status bar.
                windowInsets = WindowInsets(0, 0, 0, 0),
                // topAppBarColors, not centerAlignedTopAppBarColors: it is
                // what CenterAlignedTopAppBar itself defaults to in this
                // Material 3, checked against the reference rather than
                // remembered.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    // Every title in the accent (the user, 2026-10-31), as Modules' SettingsSectionTitle.
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/**
 * What scrolls under the floating top bar fades out instead of meeting an opaque edge, as the panes
 * kit's FloatingFrame does under its header (ui/component/FloatingPane.kt:244-310, strength computed
 * at :279-283) through [softTopEdge] (Android's FadedEdgeBox,
 * https://github.com/android/snippets/blob/main/compose/snippets/src/main/java/com/example/compose/snippets/graphics/GraphicsModifiersSnippets.kt).
 * Put it on the scrolling container itself.
 *
 * [barHeight] is the Scaffold's top inner padding: Scaffold measures its topBar slot and hands that
 * height (status bar and the bar's 8 dp margins included) to the content, which is exactly the
 * bar's own measured height. Reading it there needs no state shared between the bar and the list,
 * and works for the screens whose bar is fixed as well as for the folding one. A folding bar gives
 * [foldOffset], its scroll behavior's state.heightOffset (negative, 0 by default): the visible
 * bottom follows it but never goes above the status bar, so text stays faded under the clock. It
 * is a lambda, not the TopAppBarScrollBehavior, so the experimental Material API stays out of
 * this signature. The height starts at the top of the window, the list being drawn under the
 * status bar.
 *
 * [scrolled] is how far the content has left its top, in px (see [scrolledFromTop]). The strength is
 * that over the visible bar height, from 0 to 1, as FloatingFrame does: nothing is erased at rest.
 */
@Composable
fun Modifier.underFloatingTopBar(
    barHeight: Dp,
    scrolled: () -> Float,
    foldOffset: () -> Float = { 0f }
): Modifier {
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    // Read while drawing, so the fold redraws the fade without recomposing the list.
    val height: () -> Dp = {
        with(density) { (barHeight + foldOffset().toDp()).coerceAtLeast(statusTop) }
    }
    val strength: () -> Float = {
        val fade = with(density) { height().toPx() }
        if (fade <= 0f) 0f else (scrolled() / fade).coerceIn(0f, 1f)
    }
    return softTopEdge(height, strength)
}

/**
 * How far a lazy list has scrolled from its top, in px: the first item's offset while it is the
 * first visible one, else a number large enough for a full fade. Cheap to read when drawing
 * (LazyListState.firstVisibleItemIndex and firstVisibleItemScrollOffset are snapshot state).
 */
fun LazyListState.scrolledFromTop(): Float =
    if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE

/** The same for a lazy grid (LazyGridState has the same two properties). */
fun LazyGridState.scrolledFromTop(): Float =
    if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE
