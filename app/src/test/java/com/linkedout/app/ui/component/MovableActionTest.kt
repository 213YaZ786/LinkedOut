package com.linkedout.app.ui.component

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The snap math of MovableAction, from PipSnapAlgorithmTest, same display, same stack and same movement bounds.
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-24.0/libs/WindowManager/Shell/tests/unittest/src/com/android/wm/shell/common/pip/PipSnapAlgorithmTest.java
 * Ported from MTGA:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/ui/component/MovableActionTest.kt#MovableActionTest
 * not ported: testApplySnapFraction_stashedLeft, _stashedRight, _stashedRight_withInset, _notStashed_isNotOffBounds
 * (no stash: the button is never moved off the page), testSnapRectToClosestEdge_topEdge, _bottomEdge and
 * testGetSnapFraction_topEdge, _bottomEdge (only the left and right sides are kept).
 */
class MovableActionTest {
    private val displayBounds = IntRect(0, 0, 2000, 2000)
    private val stackBoundsCentered = IntRect(900, 900, 1100, 1100)
    private val movementBounds = IntRect(0, 0, displayBounds.width - stackBoundsCentered.width, displayBounds.width - stackBoundsCentered.width)

    @Test
    fun testApplySnapFraction_topEdge() {
        val bounds = applySnapFraction(stackBoundsCentered, movementBounds, 0.25f)

        assertEquals(movementBounds.width / 4, bounds.left)
        assertEquals(movementBounds.top, bounds.top)
    }

    @Test
    fun testApplySnapFraction_rightEdge() {
        val bounds = applySnapFraction(stackBoundsCentered, movementBounds, 1.5f)

        assertEquals(movementBounds.right, bounds.left)
        assertEquals(movementBounds.height / 2, bounds.top)
    }

    @Test
    fun testApplySnapFraction_bottomEdge() {
        val bounds = applySnapFraction(stackBoundsCentered, movementBounds, 2.25f)

        assertEquals((movementBounds.width * 0.75f).toInt(), bounds.left)
        assertEquals(movementBounds.bottom, bounds.top)
    }

    @Test
    fun testApplySnapFraction_leftEdge() {
        val bounds = applySnapFraction(stackBoundsCentered, movementBounds, 3.75f)

        assertEquals(movementBounds.left, bounds.left)
        assertEquals((movementBounds.height * 0.25f).toInt(), bounds.top)
    }

    @Test
    fun testSnapRectToClosestEdge_rightEdge() {
        // Move the centered rect slightly to the right side.
        val bounds = snapRectToClosestEdge(stackBoundsCentered.translate(IntOffset(10, 0)), movementBounds, 0)

        assertEquals(movementBounds.right, bounds.left)
    }

    @Test
    fun testSnapRectToClosestEdge_leftEdge() {
        // Move the centered rect slightly to the left side.
        val bounds = snapRectToClosestEdge(stackBoundsCentered.translate(IntOffset(-10, 0)), movementBounds, 0)

        assertEquals(movementBounds.left, bounds.left)
    }

    @Test
    fun testGetSnapFraction_leftEdge() {
        val snapFraction = getSnapFraction(stackBoundsCentered.translate(IntOffset(-10, 0)), movementBounds)

        assertEquals(3.5f, snapFraction, 0.1f)
    }

    @Test
    fun testGetSnapFraction_rightEdge() {
        val snapFraction = getSnapFraction(stackBoundsCentered.translate(IntOffset(10, 0)), movementBounds)

        assertEquals(1.5f, snapFraction, 0.1f)
    }

    // The fraction kept on one window, applied on another: same side, same share of the height.
    @Test
    fun testSnapFraction_anotherWindowSize() {
        val phone = IntRect(48, 150, 900, 2100)
        val tablet = IntRect(48, 150, 1700, 1000)
        val button = IntSize(144, 144)
        val right = snapRectToClosestEdge(IntRect(IntOffset(700, 1125), button), phone, 0)
        val onTablet = applySnapFraction(right, tablet, getSnapFraction(right, phone))
        assertEquals(tablet.right, onTablet.left)
        assertEquals((right.top - phone.top).toFloat() / phone.height, (onTablet.top - tablet.top).toFloat() / tablet.height, 0.01f)

        val left = snapRectToClosestEdge(IntRect(IntOffset(100, 600), button), phone, 0)
        assertEquals(tablet.left, applySnapFraction(left, tablet, getSnapFraction(left, phone)).left)
        // The default, bottom right, is the fraction 2.
        val corner = applySnapFraction(IntRect(IntOffset.Zero, button), phone, 2f)
        assertEquals(IntOffset(phone.right, phone.bottom), corner.topLeft)
    }
}
