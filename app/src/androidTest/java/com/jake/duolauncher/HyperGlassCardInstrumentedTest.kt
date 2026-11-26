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
 * what is on it is split into colour while its border is not, and that with the setting off the panel is the frosted glass it was.
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
                    Box(Modifier.requiredSize(240.dp).testTag("card").frostedGlass(Corner.xlarge, Color.Black.copy(alpha = .2f), elevated = false) { this }) {
                        // Something with edges on the card, near its side, as a word or an icon would be: a white bar.
                        Box(Modifier.padding(start = 196.dp, top = 80.dp).requiredSize(width = 12.dp, height = 80.dp).background(Color.White))
                    }
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

    /** The most red leads blue by, and blue leads red by, among the pixels of row [y] from [from] until [to]. */
    private fun leads(picture: Bitmap, y: Int, from: Int, to: Int): Pair<Int, Int> {
        var red = 0; var blue = 0
        for (x in from until to) { val p = picture.getPixel(x, y); red = maxOf(red, r(p) - b(p)); blue = maxOf(blue, b(p) - r(p)) }
        return red to blue
    }

    @Test fun thePaneHasGrainAndWhatIsOnItIsSplitIntoColourButItsBorderIsNot() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
        val picture = card(hyper = true)
        assertTrue("the pane is grainy (${roughness(picture)})", roughness(picture) > 2f)
        val y = picture.height / 2
        // The bar near the card's right side: red is read from further out, so one side of the bar is red and the other blue.
        val (red, blue) = leads(picture, y, picture.width * 2 / 3, picture.width - picture.width / 12)
        assertTrue("red leads at one side of the bar ($red)", red > 60)
        assertTrue("blue leads at the other ($blue)", blue > 60)
        // The card's own edge has no colour: the border is plain (it was drawn once in red and once in blue). Averaged down the edge,
        // since the grain, split like everything else, gives single pixels a little colour of their own.
        val left = tint(picture, 0, 8); val right = tint(picture, picture.width - 8, picture.width)
        assertTrue("no coloured rim on the left ($left)", kotlin.math.abs(left) < 10f)
        assertTrue("nor on the right ($right)", kotlin.math.abs(right) < 10f)
    }

    /** How far red leads blue on average over columns [from] until [to], in the middle half of the card's height. */
    private fun tint(picture: Bitmap, from: Int, to: Int): Float {
        var sum = 0; var n = 0
        for (y in picture.height / 4 until picture.height * 3 / 4) for (x in from until to) { val p = picture.getPixel(x, y); sum += r(p) - b(p); n++ }
        return sum.toFloat() / n
    }

    @Test fun offNothingOnThePanelIsSplit() {
        val picture = card(hyper = false)
        val (red, blue) = leads(picture, picture.height / 2, picture.width * 2 / 3, picture.width - picture.width / 12)
        assertTrue("no colour at the bar's sides ($red, $blue)", red < 12 && blue < 12)
    }

    @Test fun offItIsTheFrostedGlassItWas() {
        val picture = card(hyper = false)
        assertTrue("frosted glass is smooth (${roughness(picture)})", roughness(picture) < 1f)
    }
}
