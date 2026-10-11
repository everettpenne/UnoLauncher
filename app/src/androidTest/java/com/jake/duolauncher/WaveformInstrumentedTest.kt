package com.jake.duolauncher

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The island's waveform, watched on the real display in a plain activity (no test clock): it moves while playing and is still when paused. */
@RunWith(AndroidJUnit4::class)
class WaveformInstrumentedTest {
    private fun frames(animate: Boolean, count: Int, gapMs: Long): List<Int> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, EqualizerHostActivity::class.java).putExtra("animate", animate)
        ActivityScenario.launch<EqualizerHostActivity>(intent).use {
            Thread.sleep(800)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            return (0 until count).map {
                Thread.sleep(gapMs)
                val shot = automation.takeScreenshot()
                // The middle of the screen, where the bars are.
                val crop = Bitmap.createBitmap(shot, shot.width / 2 - 60, shot.height / 2 - 30, 120, 60)
                IntArray(crop.width * crop.height).also { px -> crop.getPixels(px, 0, crop.width, 0, 0, crop.width, crop.height) }.contentHashCode()
            }
        }
    }

    @Test fun theBarsMoveWhilePlaying() {
        val seen = frames(animate = true, count = 8, gapMs = 120)
        assertTrue("the bars changed between frames: $seen", seen.toSet().size >= 4)
    }

    @Test fun theBarsSitStillWhenPaused() {
        assertEquals(1, frames(animate = false, count = 4, gapMs = 150).toSet().size)
    }
}
