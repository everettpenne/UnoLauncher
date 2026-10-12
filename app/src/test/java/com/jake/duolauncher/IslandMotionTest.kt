package com.jake.duolauncher

import com.jake.duolauncher.island.*

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandMotionTest {
    private fun frame(l: Float, t: Float, w: Float, h: Float) = IslandFrame(l, t, w, h, null)

    @Test fun nearlyMeansWithinAPixel() {
        assertTrue(frame(10f, 20f, 100f, 40f).nearly(frame(10.5f, 20.4f, 100.6f, 39.5f)))
        assertFalse(frame(10f, 20f, 100f, 40f).nearly(frame(12f, 20f, 100f, 40f)))
        assertFalse(frame(10f, 20f, 100f, 40f).nearly(frame(10f, 20f, 100f, 60f)))
    }

    @Test fun unionHoldsBothFrames() {
        val u = frame(100f, 10f, 80f, 40f).union(frame(60f, 10f, 160f, 200f))
        assertEquals(60f, u.left, 0f); assertEquals(10f, u.top, 0f)
        assertEquals(160f, u.width, 0f); assertEquals(200f, u.height, 0f)
        // Taking the union with itself changes nothing, and it never shrinks.
        val f = frame(5f, 5f, 50f, 50f)
        assertEquals(f, f.union(f))
        assertTrue(u.union(frame(100f, 10f, 80f, 40f)).width >= u.width)
    }

    @Test fun anEventFlashRingsThePillInTheAppsColourWhileItShows() {
        val tint = Color(0xFF3366FF)
        val ring = IslandRingLogic.choose(true, false, false, false, 0f, false, null, Color.Red, Color.Green, tint)
        assertNotNull(ring); assertEquals(tint, ring!!.color); assertEquals(1f, ring.fraction, 0f)
    }

    @Test fun noFlashTintMeansTheOldBehaviour() {
        assertNull(IslandRingLogic.choose(true, false, false, false, 0f, false, null, Color.Red, Color.Green))
        assertNull(IslandRingLogic.choose(true, true, false, false, 0f, false, null, Color.Red, Color.Green, Color.Blue))   // expanded: none
        assertNull(IslandRingLogic.choose(true, false, true, false, 0f, false, null, Color.Red, Color.Green, Color.Blue))   // ringing: none
    }
}
