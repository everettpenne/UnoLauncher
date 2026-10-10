package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The preview shown while placing a widget must sit exactly where the widget ends up. */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewAlignmentIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(90_000) { !model().state.value.loading }

    private fun shot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.filesDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun placeAt(cell: Int, builtin: Int, tag: String): String {
        compose.onNodeWithTag("home-cell-$cell").performTouchInput { longClick() }
        compose.onNodeWithTag("empty-space-widgets").performClick()
        compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true).performScrollToNode(hasTestTag("widget-builtin-$builtin"))
        compose.onNodeWithTag("widget-builtin-$builtin", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        val preview = compose.onNodeWithTag("widget-placement-preview").fetchSemanticsNode().boundsInRoot
        shot("preview-$tag.png")
        compose.onNodeWithTag("widget-placement-apply").performClick()
        compose.waitForIdle()
        val slot = model().state.value.layout.widgetPlacements.maxOf { it.slot }
        val placed = compose.onNodeWithTag("widget-slot-$slot").fetchSemanticsNode().boundsInRoot
        shot("placed-$tag.png")
        return "$tag: preview top=${preview.top} left=${preview.left} h=${preview.height}; placed top=${placed.top} left=${placed.left} h=${placed.height}"
    }

    @Test fun thePreviewSitsWhereTheWidgetEndsUp() {
        ready()
        val before = model().state.value.layout
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val report = listOf(0 to "row0", 12 to "row3").map { (cell, tag) -> placeAt(cell, UnoWidgets.MONTH, tag).also { compose.runOnIdle { model().removePlacement(DropTarget.Widget(model().state.value.layout.widgetPlacements.maxOf { it.slot })) } } }
            File(compose.activity.filesDir, "alignment.txt").writeText(report.joinToString("\n"))
            report.forEach { line ->
                val n = Regex("preview top=([0-9.]+) left=([0-9.]+) h=([0-9.]+); placed top=([0-9.]+) left=([0-9.]+) h=([0-9.]+)").find(line)!!.groupValues.drop(1).map { it.toFloat() }
                assertEquals("$line (top)", n[3], n[0], 3f); assertEquals("$line (left)", n[4], n[1], 3f); assertEquals("$line (height)", n[5], n[2], 3f)
            }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }
}
