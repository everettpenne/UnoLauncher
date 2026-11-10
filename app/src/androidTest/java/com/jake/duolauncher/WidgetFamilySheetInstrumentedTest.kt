package com.jake.duolauncher

import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.widgets.*

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every Uno widget side by side, at the iOS layout's small size (the Lock Screen clock at medium), on their sample data, as cards in light and in dark:
 * pictures to hold the family against itself. Written to the app's files directory as widgets-<light|dark>-<page>.png; the test asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
class WidgetFamilySheetInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sheets() {
        // The wide one on a row of its own, after the small ones.
        val small = UnoWidgets.all.filter { it.spanX < 4 }; val wide = UnoWidgets.all.filter { it.spanX >= 4 }
        val pages = small.chunked(6).map { p -> p.chunked(2) } + listOf(wide.map { listOf(it) })
        var page by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        compose.setContent {
            DuoAppearanceRuntime.dark = dark
            key(dark) {
                DuoTheme(dark) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(if (dark) listOf(Color(0xFF14233A), Color(0xFF3A1F3D)) else listOf(Color(0xFFBFD8E8), Color(0xFFE8D2C0)))),
                        contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            pages[page].forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    row.forEach { def ->
                                        val w = if (def.spanX >= 4) 348.dp else 165.dp
                                        Box(Modifier.size(w, 165.dp)) {
                                            CompositionLocalProvider(LocalWidgetPreview provides true, LocalWidgetStyle provides WidgetStyle.CARD) {
                                                def.face(PreviewSamples.slotFor(def.id)) {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (d in listOf(false, true)) {
            dark = d
            for (p in pages.indices) {
                page = p
                compose.waitForIdle(); Thread.sleep(1_500); compose.waitForIdle()
                val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.filesDir, "widgets-${if (d) "dark" else "light"}-$p.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        DuoAppearanceRuntime.dark = false
    }
}
