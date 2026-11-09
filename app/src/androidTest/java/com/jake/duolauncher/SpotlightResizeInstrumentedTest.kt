package com.jake.duolauncher

import com.jake.duolauncher.island.*
import com.jake.duolauncher.search.*
import com.jake.duolauncher.appearance.*

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The results card grows and shrinks to fit as you type, frame by frame, rather than jumping to each new height. */
@RunWith(AndroidJUnit4::class)
class SpotlightResizeInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun height() = compose.onNodeWithTag("spotlight-results", useUnmergedTree = true).fetchSemanticsNode().size.height

    private fun type(text: String) = compose.onNodeWithTag("spotlight-field", useUnmergedTree = true).performTextReplacement(text)

    private fun frames(n: Int): List<Int> = List(n) { compose.mainClock.advanceTimeByFrame(); height() }

    @Test fun theCardFollowsItsResultsSmoothlyEvenWhenALetterLandsMidWay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val apps = listOf("Calculator", "Calendar", "Camera", "Cards", "Clock", "Contacts", "Chrome", "Maps", "Music", "Notes", "Photos", "Files").map {
            AppEntry("com.example.${it.lowercase()}/.Main", it, icon)
        }
        val pill = IslandFrame(left = 300f, top = 60f, width = 480f, height = 100f, hole = null)
        compose.setContent {
            DuoTheme(false) {
                Box(Modifier.fillMaxSize()) {
                    Spotlight(null, apps, emptySet(), emptyList(), false, HandoffResolver(context, ExtrasState()),
                        onLaunch = {}, onOpenFeedLink = {}, onOpenContact = {}, onDismiss = {}, progress = { 1f }, pill = pill)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_500)

        // One typed letter: many results, so the card has a long way to grow.
        val start = height()
        type("c")
        val growing = frames(70)
        val end = growing.last()
        println("RESIZE start=$start growing=$growing")
        val travel = abs(end - start)
        assertTrue("the results should change the card's height: $start to $end", travel > 100)
        assertEquals("it has settled", end, frames(10).last())
        val steps = (listOf(start) + growing).zipWithNext { a, b -> abs(b - a) }
        assertTrue("no frame jumps more than a quarter of the way: $steps", steps.max() <= travel / 4)
        assertTrue("it moves over many frames: $growing", growing.distinct().size >= 8)

        // A letter that narrows the results while the card is still moving carries on from where it is.
        type("ca"); val first = frames(5)
        val beforeLetter = first.last()
        type("cam"); val second = frames(70)
        println("RESIZE interrupted first=$first second=$second")
        val interruptionStep = abs(second.first() - beforeLetter)
        val remaining = abs(second.last() - beforeLetter).coerceAtLeast(1)
        assertTrue("the new letter does not jump the card: step $interruptionStep of $remaining left, $second", interruptionStep <= maxOf(remaining / 3, 12))
        compose.mainClock.autoAdvance = true
    }
}
