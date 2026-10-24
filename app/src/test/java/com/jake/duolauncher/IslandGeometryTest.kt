package com.jake.duolauncher

import com.jake.duolauncher.island.*

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
        // A punch hole: the pill is the hole's own height, with half a dp of island above and below it.
        assertTrue(hole.top >= 0f && hole.bottom <= frame.height)
        assertEquals("as tall as the hole", centered.height + 2f * IslandGeometry.PUNCH_REST_RING_DP * d, frame.height, 0.5f)
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
        // Round a punch hole the pill is always the hole's height; the scale only changes the room for content beside it.
        assertEquals(small.height, large.height, 0.5f)
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
        // An 80 px (30 dp) hole was over the old 28 dp punch-hole limit, so it was padded as a notch; it is the hole's height now.
        assertEquals("as tall as the hole at any size", visible.height + 2f * IslandGeometry.PUNCH_REST_RING_DP * d, small.height, .5f)
        assertEquals(small.height, large.height, .5f)
        assertTrue("width should change", large.width - small.width >= 32f)
        for (frame in listOf(small, large)) {
            val hole = frame.hole!!
            assertTrue(hole.top >= 0f && hole.bottom <= frame.height)
            assertTrue("pill clears the top edge", frame.top >= 4f * d - .5f)
        }
    }

    @Test fun withOnlyTheBoundingRectangleThePillHugsItAndStaysOffTheTopEdge() {
        // If no cutout path is available the rectangle is all there is: the pill is its height, and the slider changes the width.
        val camera = PxRect(486f, 30f, 594f, 140f)
        val small = IslandGeometry.frame(env(camera), d, 0f, scale = 0f)
        val large = IslandGeometry.frame(env(camera), d, 0f, scale = 1f)
        assertEquals(small.height, large.height, .5f)
        assertTrue(large.width > small.width)
        assertTrue(small.top >= 4f * d - .5f && large.top >= 4f * d - .5f)
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

    @Test fun theRefinedHoleSetsTheHeightAndTheSliderOnlyTheWidth() {
        val hole = IslandGeometry.refine(tallRect, visibleHole)
        val frames = listOf(0f, .25f, .5f, .75f, 1f).map { IslandGeometry.frame(env(hole), d, 0f, it) }
        frames.forEach { assertEquals("as tall as the hole", hole.height + 2f * IslandGeometry.PUNCH_REST_RING_DP * d, it.height, .5f) }
        frames.zipWithNext().forEach { (a, b) -> assertTrue("widths grow", b.width > a.width) }
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

    @Test fun eventTitlesGetMoreRoomAtALargerFontButNeverNarrow() {
        val chars = "Do Not Disturb".length
        // A larger system font needs more room for the same title, so it truncates less.
        assertTrue(IslandGeometry.eventExtraWidthDp(chars, .5f, 1.5f) > IslandGeometry.eventExtraWidthDp(chars, .5f))
        // A smaller font never narrows the pill below its base width.
        assertEquals(IslandGeometry.eventExtraWidthDp(chars, .5f), IslandGeometry.eventExtraWidthDp(chars, .5f, .8f), 0f)
        // The cap still holds at any font scale.
        assertTrue(IslandGeometry.eventExtraWidthDp(chars, .5f, 2f) <= 140f)
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
    @Test fun theRefinedHoleGivesAPillAsTallAsTheCameraAndTheTallRectangleDoesNot() {
        val tallRect = PxRect(484f, 0f, 596f, 130f)        // what the display reports: starts at the top edge
        val hole = PxRect(514f, 40f, 566f, 92f)             // the actual punch hole, from the cutout path
        val refined = IslandGeometry.holeFor(listOf(tallRect), hole, 1080f, 2424f)!!
        assertEquals(hole, refined)
        fun height(cutout: PxRect, size: Float) =
            IslandGeometry.frame(IslandEnvironment(cutout, 1080f, 80f, screenHeight = 2424f), 2.625f, 0f, size).height
        assertEquals("with the refined hole the pill is the hole's height", hole.height + 2f * IslandGeometry.PUNCH_REST_RING_DP * 2.625f, height(refined, .5f), 0.5f)
        assertTrue("the raw rectangle would make it far taller than the camera", height(tallRect, .5f) > height(refined, .5f) + 50f)
        assertEquals(tallRect, IslandGeometry.holeFor(listOf(tallRect), null, 1080f, 2424f))
    }

    // ---- the overlay is compact when idle so it does not cover the status bar's icons ----
    @Test fun aCompactIslandHugsTheCameraAndFullWidthIsUnchanged() {
        val c = PxRect(514f, 40f, 566f, 92f)
        val e = IslandEnvironment(c, 1080f, 80f, screenHeight = 2424f)
        val full = IslandGeometry.frame(e, 2.625f, 0f, .5f)
        val compact = IslandGeometry.frame(e, 2.625f, 0f, .5f, compactness = 1f)
        assertEquals("default is the old full width", full.width, IslandGeometry.frame(e, 2.625f, 0f, .5f, compactness = 0f).width, .01f)
        assertTrue("much narrower", compact.width < full.width / 1.8f)
        assertTrue("still wraps the hole", compact.left <= c.left && compact.left + compact.width >= c.right)
        assertEquals("still centred on the camera", c.centerX, compact.left + compact.width / 2f, 1f)
        assertEquals("same height, so the size setting still works", full.height, compact.height, .01f)
        val half = IslandGeometry.frame(e, 2.625f, 0f, .5f, compactness = .5f).width
        assertTrue(half > compact.width && half < full.width)
    }

    // ---- the "Extra wide" island: as wide as the 75% size setting, at the slider's own height, clamped to the phone ----
    @Test fun extraWideTakesTheWidthOfThe75PercentSizeAndKeepsTheHeightAndLeavesThePanelAlone() {
        val camera = PxRect(514f, 40f, 566f, 92f)
        val e = env(camera)
        val wide = IslandGeometry.frame(e, d, 0f, .5f)
        val extra = IslandGeometry.frame(e, d, 0f, .5f, wideFactor = 1.5f)
        // The width is what the 75% size setting gives, 1% wider; the height is the slider's own.
        assertEquals(IslandGeometry.frame(e, d, 0f, .75f).width * IslandGeometry.EXTRA_WIDE_BOOST, extra.width, .01f)
        assertTrue(extra.width > wide.width)
        assertEquals(wide.height, extra.height, .01f)
        // Above 75% it never narrows.
        assertTrue(IslandGeometry.frame(e, d, 0f, 1f, wideFactor = 1.5f).width >= IslandGeometry.frame(e, d, 0f, 1f).width)
        assertEquals("still centred on the camera", camera.centerX, extra.left + extra.width / 2f, .5f)
        assertTrue("still wraps the hole", extra.hole!!.left >= 0f && extra.hole.right <= extra.width)
        // The expanded panel is unchanged: extra width belongs to the collapsed pill alone.
        assertEquals(IslandGeometry.frame(e, d, 1f, .5f).width,
            IslandGeometry.frame(e, d, 1f, .5f, wideFactor = 1.5f).width, .5f)
        // 1f is today's width: no change.
        assertEquals(wide.width, IslandGeometry.frame(e, d, 0f, .5f, wideFactor = 1f).width, 0f)
    }

    @Test fun extraWideClampsSoThePillAndPanelStillClearTheDock() {
        // A narrow phone (~411 dp) with a 56 dp dock, at the largest size setting: extra wide would overshoot the
        // screen's usable width, so it clamps to the screen minus the dock clearance, and the panel still clears the dock.
        val narrowW = 411f * d
        val camera = PxRect(narrowW / 2f - 37f, 40f, narrowW / 2f + 37f, 92f)
        val dockPx = 56f * d
        val e = IslandEnvironment(camera, narrowW, 80f, dockWidthPx = dockPx)
        val extra = IslandGeometry.frame(e, d, 0f, 1f, wideFactor = 1.5f)
        val dockLeft = narrowW - dockPx - 16f * d
        assertTrue("pill right edge clears the dock", extra.left + extra.width <= dockLeft + .5f)
        assertTrue("pill stays within its own edge margins", extra.left >= 8f * d - .01f && extra.left + extra.width <= narrowW - 8f * d + .01f)
        assertTrue("still wraps the hole", extra.left <= camera.left + .01f && extra.left + extra.width >= camera.right - .01f)
        val open = IslandGeometry.frame(e, d, 1f, 1f, wideFactor = 1.5f)
        assertTrue("expanded panel still clears the dock", open.left + open.width <= dockLeft + .5f)
    }

    // ---- regression: 0.23.4 crashed on launch with "Cannot coerce value to an empty range" ----
    @Test fun noCombinationOfCameraDensitySizeAndCompactnessMakesTheFrameThrow() {
        val densities = listOf(1.5f, 2f, 2.625f, 2.75f, 3f, 3.5f)
        val sizes = listOf(0f, .25f, .5f, .75f, 1f)
        val compactness = listOf(0f, .5f, 1f)
        val progress = listOf(0f, .5f, 1f)
        for (d in densities) for (w in listOf(30f, 52f, 61.4f, 70f, 90f, 130f)) for (h in listOf(30f, 52f, 61.4f, 90f))
            for (top in listOf(0f, 12f, 25f, 40f, 66f)) for (x in listOf(w / 2f + 2f, 300f, 540f, 1080f - w / 2f - 2f))
                for (size in sizes) for (c in compactness) for (p in progress) {
                    val cutout = PxRect(x - w / 2f, top, x + w / 2f, top + h)
                    val e = IslandEnvironment(cutout, 1080f, 80f, screenHeight = 2424f)
                    val f = IslandGeometry.frame(e, d, p, size, compactness = c)   // must not throw
                    assertTrue("wraps the hole: d=$d w=$w h=$h size=$size c=$c p=$p",
                        f.left <= cutout.left + .01f && f.left + f.width >= cutout.right - .01f)
                }
    }

    @Test fun theReportedCrashCaseDoesNotThrow() {
        // Roughly the report: a hole near x=488 on a 1080 px wide screen with the compact idle width.
        val d = 2.75f
        val cutout = PxRect(488.29358f - 2f, 40f, 488.29358f + 56f, 98f)
        val e = IslandEnvironment(cutout, 1080f, 120f, screenHeight = 2424f)
        for (size in listOf(0f, .5f, 1f)) IslandGeometry.frame(e, d, 0f, size, compactness = 1f)
    }

    @Test fun aBounceCarriesTheIslandPastItsOpenSizeButNeverOffTheScreen() {
        val camera = PxRect(screenW / 2f - 30f, 40f, screenW / 2f + 30f, 100f)
        val e = env(camera)
        val open = IslandGeometry.frame(e, 2f, 1f)
        val over = IslandGeometry.frame(e, 2f, 1.25f)
        assertTrue("width overshoots", over.width > open.width)
        assertTrue("height overshoots", over.height > open.height)
        val most = IslandGeometry.frame(e, 2f, 9f)
        assertEquals("the overshoot is capped", IslandGeometry.frame(e, 2f, IslandGeometry.MAX_OVERSHOOT).width, most.width, .01f)
        assertTrue("never wider than the screen", most.left >= 0f && most.left + most.width <= screenW)
        assertEquals("a spring dipping below closed leaves the pill alone", IslandGeometry.frame(e, 2f, 0f).width, IslandGeometry.frame(e, 2f, -.4f).width, 0f)
    }

    @Test fun widthAndHeightOpenIndependentlyForStretchAndSquash() {
        val camera = PxRect(screenW / 2f - 30f, 40f, screenW / 2f + 30f, 100f)
        val e = env(camera)
        val closed = IslandGeometry.frame(e, 2f, 0f)
        val open = IslandGeometry.frame(e, 2f, 1f)
        val drop = IslandGeometry.frame(e, 2f, 0f, heightProgress = 1f)   // tall and narrow: a drop
        assertEquals(closed.width, drop.width, 0f)
        assertEquals(open.height, drop.height, 0f)
        val pancake = IslandGeometry.frame(e, 2f, 1f, heightProgress = 0f)  // wide and flat
        assertEquals(open.width, pancake.width, 0f)
        assertEquals(closed.height, pancake.height, 0f)
        // Unless told otherwise the two move together, as before.
        assertEquals(IslandGeometry.frame(e, 2f, .6f), IslandGeometry.frame(e, 2f, .6f, heightProgress = .6f))
    }
}
