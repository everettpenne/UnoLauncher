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

    @Test fun anExtraBodyRowMakesTheExpandedPanelTallerAndLeavesTheCollapsedPillAlone() {
        val plain = IslandGeometry.frame(env(centered), d, 1f, .5f)
        val withRow = IslandGeometry.frame(env(centered), d, 1f, .5f, extraBodyDp = MEDIA_ROW_DP)
        assertEquals(MEDIA_ROW_DP * d, withRow.height - plain.height, 0.5f)
        assertEquals(plain.top, withRow.top, 0.01f)
        assertEquals(IslandGeometry.frame(env(centered), d, 0f, .5f).height,
            IslandGeometry.frame(env(centered), d, 0f, .5f, extraBodyDp = MEDIA_ROW_DP).height, 0.01f)
    }

    @Test fun anEventWidensTheCollapsedPillAroundTheHoleWithoutMovingIt() {
        val plain = IslandGeometry.frame(env(centered), d, 0f, .5f)
        val wide = IslandGeometry.frame(env(centered), d, 0f, .5f, extraWidthDp = 44f)
        assertEquals(44f * d, wide.width - plain.width, 0.5f)
        assertEquals("still centered on the hole", centered.centerX, wide.left + wide.width / 2f, 0.5f)
        assertEquals(plain.height, wide.height, 0.01f)
        assertEquals("the expanded panel is unaffected",
            IslandGeometry.frame(env(centered), d, 1f, .5f).width,
            IslandGeometry.frame(env(centered), d, 1f, .5f, extraWidthDp = 44f).width, 0.5f)
    }

    @Test fun longerEventTitlesGetMoreRoomAndShortOnesStillGrowAFixedAmount() {
        val short = IslandGeometry.eventExtraWidthDp("Silent".length, .5f)
        val long = IslandGeometry.eventExtraWidthDp("Do Not Disturb".length, .5f)
        assertTrue("short titles grow a little: $short", short >= 24f)
        assertTrue("longer titles grow more: $long vs $short", long > short)
        assertTrue("a long title needs real room: $long", long >= 60f)
        assertTrue("capped so the pill cannot swallow the screen", IslandGeometry.eventExtraWidthDp(80, 0f) <= 140f)
        // A bigger size setting already has wider side slots, so it needs less extra.
        assertTrue(IslandGeometry.eventExtraWidthDp(14, 1f) < IslandGeometry.eventExtraWidthDp(14, 0f))
    }

    // ---- phone turned sideways: the camera is on a left or right edge ----
    private val landscapeW = 2424f; private val landscapeH = 1080f
    private fun sideEnv(cutout: PxRect?, dock: Float = 0f) = IslandEnvironment(cutout, landscapeW, 63f, dock, landscapeH)

    @Test fun aSmallRectangleOnASideEdgeIsTheCameraButAWaterfallStripIsNot() {
        val camera = PxRect(0f, 472f, 136f, 608f)
        assertEquals(camera, IslandGeometry.pickCutout(listOf(camera), landscapeW, landscapeH))
        val rightCamera = PxRect(landscapeW - 136f, 472f, landscapeW, 608f)
        assertEquals(rightCamera, IslandGeometry.pickCutout(listOf(rightCamera), landscapeW, landscapeH))
        val waterfall = PxRect(0f, 0f, 60f, landscapeH)
        assertNull(IslandGeometry.pickCutout(listOf(waterfall), landscapeW, landscapeH))
        assertEquals(camera, IslandGeometry.pickCutout(listOf(waterfall, camera), landscapeW, landscapeH))
    }

    @Test fun aCutoutNearTheTopStillWinsAndPortraitIsUnchanged() {
        val top = PxRect(414f, 0f, 666f, 126f); val side = PxRect(0f, 1000f, 100f, 1100f)
        assertEquals(top, IslandGeometry.pickCutout(listOf(side, top), 1080f, 2424f))
        assertEquals(IslandSide.TOP, IslandGeometry.sideOf(top, 1080f, 2424f))
        assertEquals(IslandSide.TOP, IslandGeometry.sideOf(top, 1080f, 0f))
        assertEquals(IslandSide.LEFT, IslandGeometry.sideOf(PxRect(0f, 472f, 136f, 608f), landscapeW, landscapeH))
        assertEquals(IslandSide.RIGHT, IslandGeometry.sideOf(PxRect(landscapeW - 136f, 472f, landscapeW, 608f), landscapeW, landscapeH))
    }

    @Test fun onTheLeftEdgeTheIslandStaysOnTheCameraAsAVerticalPill() {
        val cutout = PxRect(0f, 472f, 136f, 608f)
        val f = IslandGeometry.frame(sideEnv(cutout), 2.6f, 0f)
        assertEquals(IslandSide.LEFT, f.side)
        assertEquals(0f, f.left, 0.5f)
        assertTrue("taller than wide", f.height > f.width)
        assertEquals("centred on the camera", cutout.centerY, f.top + f.height / 2f, 1f)
        assertTrue("wraps the hole", f.left <= cutout.left && f.left + f.width >= cutout.right && f.top <= cutout.top && f.top + f.height >= cutout.bottom)
        assertNotNull(f.hole); assertEquals(cutout.left - f.left, f.hole!!.left, .5f)
    }

    @Test fun onTheRightEdgeItIsMirroredAndStaysOnScreen() {
        val cutout = PxRect(landscapeW - 136f, 472f, landscapeW, 608f)
        val f = IslandGeometry.frame(sideEnv(cutout), 2.6f, 0f)
        assertEquals(IslandSide.RIGHT, f.side)
        assertEquals(landscapeW, f.left + f.width, 0.5f)
        assertTrue(f.height > f.width); assertEquals(cutout.centerY, f.top + f.height / 2f, 1f)
        val open = IslandGeometry.frame(sideEnv(cutout), 2.6f, 1f)
        assertEquals("the panel stays pinned to the edge as it opens", landscapeW, open.left + open.width, 0.5f)
        assertTrue(open.left >= 0f)
    }

    @Test fun theExpandedPanelOpensInwardWithinTheScreenAndKeepsTheCameraInsideIt() {
        val cutout = PxRect(0f, 472f, 136f, 608f)
        val open = IslandGeometry.frame(sideEnv(cutout), 2.6f, 1f, extraBodyDp = 40f)
        assertEquals(0f, open.left, 0.5f); assertTrue(open.width > 600f)
        assertTrue(open.top >= 0f && open.top + open.height <= landscapeH)
        assertTrue(open.top <= cutout.top && open.top + open.height >= cutout.bottom)
    }

    @Test fun anEventWidensTheSidePillInwardOnly() {
        val cutout = PxRect(0f, 472f, 136f, 608f)
        val idle = IslandGeometry.frame(sideEnv(cutout), 2.6f, 0f)
        val event = IslandGeometry.frame(sideEnv(cutout), 2.6f, 0f, extraWidthDp = 60f)
        assertEquals(0f, event.left, 0.5f); assertTrue(event.width > idle.width + 100f)
        assertEquals(idle.top + idle.height / 2f, event.top + event.height / 2f, 1f)
    }

    @Test fun withoutAScreenHeightTheOldPortraitBehaviourIsUsed() {
        val cutout = PxRect(414f, 0f, 666f, 126f)
        val f = IslandGeometry.frame(IslandEnvironment(cutout, 1080f, 63f), 2.6f, 0f)
        assertEquals(IslandSide.TOP, f.side); assertTrue(f.width > f.height)
    }

    // ---- the size setting needs room, which only the refined hole leaves ----
    @Test fun theSizeSettingChangesTheIslandWhenTheHoleIsRefinedButNotWhenItIsTheTallRectangle() {
        val tallRect = PxRect(484f, 0f, 596f, 130f)        // what the display reports: starts at the top edge
        val hole = PxRect(514f, 40f, 566f, 92f)             // the actual punch hole, from the cutout path
        val refined = IslandGeometry.holeFor(listOf(tallRect), hole, 1080f, 2424f)!!
        assertEquals(hole, refined)
        fun height(cutout: PxRect, size: Float) =
            IslandGeometry.frame(IslandEnvironment(cutout, 1080f, 80f, screenHeight = 2424f), 2.625f, 0f, size).height
        assertTrue("with the refined hole the slider has a range", height(refined, 1f) > height(refined, 0f) + 20f)
        assertEquals("with the raw rectangle it did nothing, which is the bug", height(tallRect, 1f), height(tallRect, 0f), 0.5f)
        assertEquals(tallRect, IslandGeometry.holeFor(listOf(tallRect), null, 1080f, 2424f))
    }
}
