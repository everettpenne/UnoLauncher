package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class IslandGeometryTest {
    private val d = 2.625f
    private val screenW = 1080f
    // A centered punch-hole like the Pixel 10a's: ~52 px across, a little below the top edge.
    private val centered = PxRect(514f, 40f, 566f, 92f)

    private fun env(cutout: PxRect?) = IslandEnvironment(cutout, screenW, statusBarHeight = 80f)

    @Test fun collapsedIslandIsCenteredOnTheHoleAndWrapsIt() {
        val frame = IslandGeometry.frame(env(centered), d, 0f)
        assertEquals("horizontally centered on the hole", centered.centerX, frame.left + frame.width / 2f, 0.5f)
        val hole = frame.hole!!
        assertEquals("vertically centered on the hole", frame.height / 2f, hole.centerY, 0.5f)
        assertTrue("hole inside the island with room around it", hole.left > 20f && hole.right < frame.width - 20f)
        assertTrue(hole.top >= 3f * d && hole.bottom <= frame.height - 3f * d)
        assertTrue("island stays on screen", frame.top >= 0f)
        // The island's own coordinates map back to the cutout's window position.
        assertEquals(centered.left, frame.left + hole.left, 0.5f)
        assertEquals(centered.top, frame.top + hole.top, 0.5f)
    }

    @Test fun holeCloseToTheTopEdgeShrinksTheIslandInsteadOfCrossingTheEdge() {
        val tight = PxRect(514f, 8f, 566f, 60f)
        val frame = IslandGeometry.frame(env(tight), d, 0f)
        assertTrue("top never goes off screen", frame.top >= 0f)
        assertTrue(frame.hole!!.top >= 0f && frame.hole!!.bottom <= frame.height)
    }

    @Test fun holeInACornerKeepsTheIslandOnScreenAndAroundTheHole() {
        val corner = PxRect(960f, 40f, 1012f, 92f)
        listOf(0f, .5f, 1f).forEach { progress ->
            val frame = IslandGeometry.frame(env(corner), d, progress)
            assertTrue("left edge on screen at $progress", frame.left >= 8f * d - .01f)
            assertTrue("right edge on screen at $progress", frame.left + frame.width <= screenW - 8f * d + .01f)
            val hole = frame.hole!!
            assertTrue("hole stays inside at $progress", hole.left >= 0f && hole.right <= frame.width)
        }
    }

    @Test fun aHoleTouchingTheScreenEdgeIsStillFullyWrapped() {
        val flush = PxRect(0f, 20f, 136f, 156f)
        listOf(0f, 1f).forEach { progress ->
            val frame = IslandGeometry.frame(env(flush), d, progress)
            val hole = frame.hole!!
            assertTrue("hole inside at $progress: $hole", hole.left >= 0f && hole.right <= frame.width)
            assertTrue(frame.left >= 0f && frame.left + frame.width <= screenW)
        }
    }

    @Test fun expandedPanelHangsBelowTheHoleAndFitsTheScreen() {
        val frame = IslandGeometry.frame(env(centered), d, 1f)
        assertTrue(frame.width <= screenW - 16f * d + .01f)
        assertEquals("expanded panel stays centered on the hole", centered.centerX, frame.left + frame.width / 2f, 0.5f)
        assertTrue("room for a body beneath the hole", frame.height - frame.hole!!.bottom > 90f * d)
        assertEquals("top edge does not move", IslandGeometry.frame(env(centered), d, 0f).top, frame.top, 0.01f)
    }

    @Test fun sizeGrowsMonotonicallyWithProgress() {
        val sizes = listOf(0f, .25f, .5f, .75f, 1f).map { IslandGeometry.frame(env(centered), d, it) }
        sizes.zipWithNext().forEach { (a, b) ->
            assertTrue(b.width >= a.width && b.height >= a.height)
        }
    }

    @Test fun withoutACutoutTheIslandIsACapsuleBelowTheStatusBar() {
        val frame = IslandGeometry.frame(env(null), d, 0f)
        assertNull(frame.hole)
        assertEquals(screenW / 2f, frame.left + frame.width / 2f, 0.5f)
        assertTrue(frame.top >= 80f)
    }

    @Test fun onlyAHoleNearTheTopCountsAsTheCamera() {
        val camera = PxRect(514f, 40f, 566f, 92f)
        val sideWaterfall = PxRect(0f, 400f, 40f, 2000f)
        val farCornerHole = PxRect(980f, 20f, 1030f, 70f)
        assertEquals(camera, IslandGeometry.pickCutout(listOf(sideWaterfall, farCornerHole, camera), screenW, 2424f))
        assertNull(IslandGeometry.pickCutout(listOf(sideWaterfall), screenW, 2424f))
        assertNull(IslandGeometry.pickCutout(emptyList(), screenW, 2424f))
    }

    @Test fun islandSizeScaleControlsTheCollapsedCapsule() {
        val camera = PxRect(514f, 40f, 566f, 92f)
        val small = IslandGeometry.frame(env(camera), d, 0f, scale = 0f)
        val large = IslandGeometry.frame(env(camera), d, 0f, scale = 1f)
        assertTrue(large.height > small.height)
        assertTrue(large.width > small.width)
        // The hole must stay fully wrapped at every size.
        assertTrue(small.hole!!.top >= 0f && small.hole.bottom <= small.height)
        assertTrue(large.hole!!.top >= 0f && large.hole.bottom <= large.height)
    }

    @Test fun islandSizeScaleShrinksTheNoCutoutCapsule() {
        val small = IslandGeometry.frame(env(null), d, 0f, scale = 0f)
        val large = IslandGeometry.frame(env(null), d, 0f, scale = 1f)
        assertTrue(large.height > small.height)
        assertTrue(large.width > small.width)
    }

    @Test fun punchHoleScaleChangesTheCapsuleVisiblyAndKeepsTheHoleWrapped() {
        // Pixel-10a-like centered punch hole. Android reports a 108x110 px (41x42 dp) bounding
        // rectangle; the visible hole inside it, taken from the cutout path, is a circle roughly
        // 80 px across. (Assumed numbers: confirm with `dumpsys window displays | grep mDisplayCutout`.)
        val camera = PxRect(486f, 30f, 594f, 140f)
        val visible = IslandGeometry.refine(camera, PxRect(500f, 44f, 580f, 124f))
        val small = IslandGeometry.frame(env(visible), d, 0f, scale = 0f)
        val large = IslandGeometry.frame(env(visible), d, 0f, scale = 1f)
        assertTrue("height should change", large.height - small.height >= 24f)
        assertTrue("width should change", large.width - small.width >= 32f)
        for (frame in listOf(small, large)) {
            val hole = frame.hole!!
            assertTrue(hole.top >= 0f && hole.bottom <= frame.height)
            assertTrue("pill clears the top edge", frame.top >= 6f * d - .5f)
        }
    }

    @Test fun withOnlyTheBoundingRectangleTheSliderStillWorksButStaysOffTheTopEdge() {
        // If no cutout path is available the rectangle is all there is: less room, same rules.
        val camera = PxRect(486f, 30f, 594f, 140f)
        val small = IslandGeometry.frame(env(camera), d, 0f, scale = 0f)
        val large = IslandGeometry.frame(env(camera), d, 0f, scale = 1f)
        assertTrue("height still changes", large.height > small.height + 8f)
        assertTrue(small.top >= 6f * d - .5f && large.top >= 6f * d - .5f)
    }

    @Test fun expandedIslandClearsTheDockStrip() {
        // Cover-width window with a 56dp dock: the expanded island must never reach the dock.
        val camera = PxRect(486f, 30f, 594f, 140f)
        val dockPx = 56f * d
        val frame = IslandGeometry.frame(
            IslandEnvironment(camera, screenW, 80f, dockWidthPx = dockPx), d, 1f)
        val dockLeft = screenW - dockPx - 16f * d
        assertTrue("island right edge must clear the dock",
            frame.left + frame.width <= dockLeft)
    }

    // A phone that reports a tall cutout rectangle starting at y = 0 even though the visible hole
    // is a small circle inside it (the emulator's "hole" overlay, and many real Pixels).
    private val tallRect = PxRect(414f, 0f, 666f, 136f)
    private val visibleHole = PxRect(500f, 36f, 588f, 124f)

    @Test fun refineUsesTheVisibleHoleInsideATallRectangle() {
        assertEquals(visibleHole, IslandGeometry.refine(tallRect, visibleHole))
        assertEquals("falls back without a path", tallRect, IslandGeometry.refine(tallRect, null))
        assertEquals("falls back for a degenerate path", tallRect,
            IslandGeometry.refine(tallRect, PxRect(600f, 60f, 601f, 61f)))
    }

    @Test fun pillNeverTouchesTheTopOfTheScreen() {
        val hole = IslandGeometry.refine(tallRect, visibleHole)
        listOf(0f, .5f, 1f).forEach { scale ->
            val frame = IslandGeometry.frame(env(hole), d, 0f, scale)
            assertTrue("top ${frame.top} at scale $scale leaves 6 dp clear", frame.top >= 6f * d - .5f)
        }
    }

    @Test fun sizeSliderChangesTheCollapsedHeightOverItsWholeRange() {
        val hole = IslandGeometry.refine(tallRect, visibleHole)
        val heights = listOf(0f, .25f, .5f, .75f, 1f).map { IslandGeometry.frame(env(hole), d, 0f, it).height }
        heights.zipWithNext().forEach { (a, b) -> assertTrue("$heights strictly grows", b > a) }
        assertTrue("never shorter than the hole", heights.first() >= hole.height)
    }

    @Test fun usingTheRawTallRectangleIsWhatMadeTheSliderADeadControl() {
        // Documents the bug this guards against: sized from the raw rectangle the height is fixed.
        val heights = listOf(0f, .5f, 1f).map { IslandGeometry.frame(env(tallRect), d, 0f, it).height }
        assertEquals(1, heights.toSet().size)
    }
}
