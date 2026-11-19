package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.appearance.Corner
import com.jake.duolauncher.search.AssistArrival
import com.jake.duolauncher.search.LightMood
import com.jake.duolauncher.search.assistantLight
import com.jake.duolauncher.search.islandAssistantLight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pictures of the assistant's light in each mood, round a pill and round a field, for looking at. Written to the app's files directory
 *  as light-<name>.png; the test asserts nothing. */
@RunWith(AndroidJUnit4::class)
class AssistantLightShotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) {
        Thread.sleep(700); compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, minOf(1500, bitmap.height))
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "light-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun moods() {
        compose.mainClock.autoAdvance = true
        compose.setContent {
            DuoTheme(true) {
                val d = androidx.compose.ui.platform.LocalDensity.current.density
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2A49), Color(0xFF3A2A4F), Color(0xFF0E1420))))) {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(46.dp)) {
                        for (mood in listOf(LightMood.THINKING, LightMood.WRITING, LightMood.WAITING, LightMood.OFF)) {
                            Box(Modifier.size(126.dp, 37.dp).islandAssistantLight(mood, { 18.5f * d }, { 1f }).background(Color.Black, com.jake.duolauncher.island.IslandOutlineShape(18.5f * d, d)))
                        }
                        for (mood in listOf(LightMood.THINKING, LightMood.WAITING)) {
                            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).background(Color(0x66000000), Corner.pill).assistantLight(mood, Corner.pill))
                        }
                    }
                }
            }
        }
        shot("moods")
    }

    @Test fun arrival() {
        // The clock is held, so the picture is of the glow at its brightest and not of whenever the screenshot happened to land.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DuoTheme(true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF20242C))) { AssistArrival(open = true) }
            }
        }
        compose.mainClock.advanceTimeBy(170)
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "light-arrival.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.mainClock.autoAdvance = true
    }
}
