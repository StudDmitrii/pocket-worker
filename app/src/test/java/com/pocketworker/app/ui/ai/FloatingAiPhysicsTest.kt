package com.pocketworker.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingAiPhysicsTest {

    private fun testBounds(
        width: Float = 1080f,
        height: Float = 2400f,
        buttonSize: Float = 180f,
        leftInset: Float = 0f,
        topInset: Float = 80f,
        rightInset: Float = 0f,
        bottomInset: Float = 120f,
        safeMargin: Float = 48f,
        snapThreshold: Float = 30f
    ) = FloatingButtonBounds(
        containerWidthPx = width,
        containerHeightPx = height,
        buttonSizePx = buttonSize,
        leftInsetPx = leftInset,
        topInsetPx = topInset,
        rightInsetPx = rightInset,
        bottomInsetPx = bottomInset,
        safeMarginPx = safeMargin,
        snapThresholdPx = snapThreshold
    )

    @Test
    fun `drop near left edge snaps half-hidden to DockSide LEFT`() {
        val bounds = testBounds()
        val drop = resolveDropTarget(releasedXPx = 20f, releasedYPx = 800f, bounds = bounds)

        assertEquals(DockSide.LEFT, drop.dockSide)
        assertEquals(-90f, drop.xPx, 0.01f) // Half of 180px button visible
        assertEquals(800f, drop.yPx, 0.01f)
    }

    @Test
    fun `drop near right edge snaps half-hidden to DockSide RIGHT`() {
        val bounds = testBounds()
        // button right edge = 885 + 180 = 1065 >= 1080 - 30 = 1050
        val drop = resolveDropTarget(releasedXPx = 885f, releasedYPx = 950f, bounds = bounds)

        assertEquals(DockSide.RIGHT, drop.dockSide)
        assertEquals(1080f - 90f, drop.xPx, 0.01f) // Half visible on right edge
        assertEquals(950f, drop.yPx, 0.01f)
    }

    @Test
    fun `drop inside screen remains undocked and clamped to safe margins`() {
        val bounds = testBounds()
        // Safe X is [48 .. 1080 - 180 - 48 = 852]; snap zone is <= 30 or >= 870
        val dropMid = resolveDropTarget(releasedXPx = 400f, releasedYPx = 600f, bounds = bounds)
        assertEquals(DockSide.NONE, dropMid.dockSide)
        assertEquals(400f, dropMid.xPx, 0.01f)
        assertEquals(600f, dropMid.yPx, 0.01f)

        // Released just outside snap threshold on left (35f) -> clamped to minSafeX (48f)
        val dropLeftSafe = resolveDropTarget(releasedXPx = 35f, releasedYPx = 10f, bounds = bounds)
        assertEquals(DockSide.NONE, dropLeftSafe.dockSide)
        assertEquals(48f, dropLeftSafe.xPx, 0.01f)
        assertEquals(bounds.minY, dropLeftSafe.yPx, 0.01f) // Clamped above top inset + margin
    }

    @Test
    fun `resolveUndockX returns safe inside edge for docked sides`() {
        val bounds = testBounds(leftInset = 24f, rightInset = 12f)
        assertEquals(bounds.minSafeX, resolveUndockX(DockSide.LEFT, bounds), 0.01f)
        assertEquals(bounds.maxSafeX, resolveUndockX(DockSide.RIGHT, bounds), 0.01f)
    }

    @Test
    fun `fractions round-trip accurately inside bounds`() {
        val bounds = testBounds()
        val x = bounds.xFromFraction(0.35f)
        val y = bounds.yFromFraction(0.75f)
        assertEquals(0.35f, bounds.xToFraction(x), 0.001f)
        assertEquals(0.75f, bounds.yToFraction(y), 0.001f)
    }
}
