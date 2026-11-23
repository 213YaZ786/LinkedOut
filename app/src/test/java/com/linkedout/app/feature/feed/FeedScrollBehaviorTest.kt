package com.linkedout.app.feature.feed

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * requests.md point 31, A profile does not scroll down: FeedScreen builds its top bar's scroll behaviour before the bar
 * exists (the bar is drawn only once the profile card has left, `headerGone`). Material's EnterAlwaysScrollBehavior
 * adds every drag to `heightOffset`, which `heightOffsetLimit` bounds, and a limit never set by a measured bar stays
 * -Float.MAX_VALUE: the whole upward drag is "used" by a bar that is not there, the list never gets it, and the card
 * never leaves. The fix is Material's own `canScroll`, `enterAlwaysScrollBehavior(state, canScroll = { headerGone })`.
 * The behaviour is built as the screen builds it, in a composition (the function is @Composable), and driven through its
 * nestedScrollConnection as the nested scroll does.
 * Ported from Material's EnterAlwaysScrollBehavior (its onPreScroll and canScroll):
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AppBar.kt#EnterAlwaysScrollBehavior
 * and the local-test recipe https://developer.android.com/training/testing/local-tests
 * not ported: onPostScroll and onPostFling (the content offset and the settle animation do not decide who gets the
 * scroll), isScrollingContentAtStart (the screen does not pass it), isPinned, snapAnimationSpec and flingAnimationSpec
 * (the defaults).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36])
class FeedScrollBehaviorTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // What the screen reads: the card still on screen, or gone.
    private var headerGone = false

    // As the screen builds it, `enterAlwaysScrollBehavior(state, canScroll = { headerGone })`, in a composition.
    private fun behaviorOf(state: TopAppBarState): TopAppBarScrollBehavior {
        lateinit var behavior: TopAppBarScrollBehavior
        compose.setContent {
            behavior = TopAppBarDefaults.enterAlwaysScrollBehavior(state, canScroll = { headerGone })
        }
        compose.waitForIdle()
        return behavior
    }

    // A state made by its constructor: the bar not measured, so the limit is the one a fresh state has.
    private fun unmeasuredState() = TopAppBarState(
        initialHeightOffsetLimit = -Float.MAX_VALUE,
        initialHeightOffset = 0f,
        initialContentOffset = 0f
    )

    private fun TopAppBarScrollBehavior.drag(y: Float): Offset =
        nestedScrollConnection.onPreScroll(Offset(0f, y), NestedScrollSource.UserInput)

    @Test
    fun cardOnScreenTheListKeepsTheScroll() {
        headerGone = false
        val state = unmeasuredState()
        val behavior = behaviorOf(state)
        assertEquals(Offset.Zero, behavior.drag(-100f))
        assertEquals(0f, state.heightOffset, 0f)
    }

    // The cause of the deadlock, kept as a test: without canScroll a bar that was never measured eats the drag.
    @Test
    fun barNeverMeasuredConsumesTheWholeDragWhenAskedToScroll() {
        headerGone = true
        val state = unmeasuredState()
        val behavior = behaviorOf(state)
        assertEquals(-Float.MAX_VALUE, state.heightOffsetLimit, 0f)
        assertEquals(Offset(0f, -100f), behavior.drag(-100f))
        assertEquals(-100f, state.heightOffset, 0f)
    }

    @Test
    fun barMeasuredFoldsThenGivesTheScrollBack() {
        headerGone = true
        val state = unmeasuredState()
        state.heightOffsetLimit = -200f
        val behavior = behaviorOf(state)

        // An upward drag folds the bar and is consumed.
        assertEquals(Offset(0f, -100f), behavior.drag(-100f))
        assertEquals(-100f, state.heightOffset, 0f)

        // The bar reaches its limit with this one: it moved 100, Material consumes the whole 150 as it changed.
        assertEquals(Offset(0f, -150f), behavior.drag(-150f))
        assertEquals(-200f, state.heightOffset, 0f)

        // Fully folded, nothing changes any more: the list gets the scroll.
        assertEquals(Offset.Zero, behavior.drag(-50f))
        assertEquals(-200f, state.heightOffset, 0f)
    }
}
