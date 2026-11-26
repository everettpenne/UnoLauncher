package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.appearance.FrostedWallpaper
import com.jake.duolauncher.appearance.HyperGlassCards
import com.jake.duolauncher.appearance.LocalFrostedWallpaper
import com.jake.duolauncher.appearance.frostedGlass
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A hyper glass card on a real screen: that its shader compiles and draws, that the pane has grain where frosted glass is smooth, that
 * its border is split into colour, and that with the setting off the panel is the frosted glass it was.
 */
@RunWith(AndroidJUnit4::class)
class HyperGlassCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @After fun off() { HyperGlassCards.on = false }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    private fun card(hyper: Boolean): Bitmap {
        HyperGlassCards.on = hyper
        val wallpaper = FrostedWallpaper().apply { image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF606060.toInt()) }.asImageBitmap() }
        compose.setContent {
            CompositionLocalProvider(LocalFrostedWallpaper provides wallpaper) {
                Box(Modifier.fillMaxSize().background(Color(0xFF606060)).padding(24.dp)) {
                    Box(Modifier.requiredSize(240.dp).testTag("card").frostedGlass(Corner.xlarge, Color.Black.copy(alpha = .2f), elevated = false) { this })
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag("card").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    }

    /** How much the green of neighbouring pixels differs along a row in the middle of the pane, on average: grain. */
    private fun roughness(picture: Bitmap): Float {
        val y = picture.height / 2
        var sum = 0; var n = 0
        for (x in picture.width / 4 until picture.width * 3 / 4) { sum += kotlin.math.abs(g(picture.getPixel(x, y)) - g(picture.getPixel(x + 1, y))); n++ }
        return sum.toFloat() / n
    }

    @Test fun thePaneHasGrainAndItsBorderIsSplitIntoColour() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
        val picture = card(hyper = true)
        assertTrue("the pane is grainy (${roughness(picture)})", roughness(picture) > 2f)
        // Half way down, red sits to the left of each edge and blue to the right, so inside the card blue leads at the left edge and
        // red at the right one (the other of each pair falls just outside the card, on the page).
        val y = picture.height / 2
        var redLed = false; var blueLed = false
        for (x in 0 until 16) { val p = picture.getPixel(x, y); if (b(p) - r(p) > 40) blueLed = true }
        for (x in picture.width - 16 until picture.width) { val p = picture.getPixel(x, y); if (r(p) - b(p) > 24) redLed = true }
        assertTrue("red leads somewhere on the border", redLed)
        assertTrue("blue leads somewhere on the border", blueLed)
    }

    @Test fun offItIsTheFrostedGlassItWas() {
        val picture = card(hyper = false)
        assertTrue("frosted glass is smooth (${roughness(picture)})", roughness(picture) < 1f)
    }
}
