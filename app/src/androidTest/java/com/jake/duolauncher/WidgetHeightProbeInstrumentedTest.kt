package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A developer aid: measures how tall placed widgets really are against the rows they should cover (not part of the suites). */
class WidgetHeightProbeInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]

    @Test fun probe() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        val before = model().state.value.layout
        val out = StringBuilder()
        try {
            val d = compose.activity.resources.displayMetrics.density
            out.appendLine("density=$d")
            for ((spanX, spanY) in listOf(2 to 1, 2 to 2, 2 to 3, 1 to 2, 4 to 1, 4 to 2)) {
                compose.runOnIdle {
                    model().state.value.order.toList().forEach { model().setPinned(it, false) }
                    model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
                }
                compose.waitForIdle()
                val layout = model().state.value.layout
                for (startRow in listOf(0, 1, 2, 3)) {
                    val index = startRow * GRID_COLUMNS
                    val cand = widgetCandidate(layout, 0, index, spanX, spanY) ?: continue
                    compose.runOnIdle { model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) } }
                    compose.runOnIdle { model().placeWidget(cand.copy(id = UnoWidgets.NOTE)) }
                    compose.waitForIdle()
                    compose.onNodeWithContentDescription("Home page 1").performClick()
                    compose.waitForIdle()
                    val w = compose.onAllNodesWithTag("uno-note-0", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: continue
                    val first = compose.onNodeWithTag("home-cell-$index").fetchSemanticsNode().boundsInRoot
                    val lastIdx = (startRow + spanY - 1) * GRID_COLUMNS
                    val last = compose.onNodeWithTag("home-cell-$lastIdx").fetchSemanticsNode().boundsInRoot
                    val expected = last.bottom - first.top
                    out.appendLine("span ${spanX}x$spanY at row $startRow: widget h=%.1fdp w=%.1fdp top=%.1f | rows cover h=%.1fdp (first cell h=%.1f, last cell h=%.1f) | extra=%.1fdp"
                        .format(w.height / d, w.width / d, w.top / d, expected / d, first.height / d, last.height / d, (w.height - expected) / d))
                }
            }
        } finally {
            File(compose.activity.getExternalFilesDir(null)!!, "height-probe.txt").writeText(out.toString())
            compose.runOnIdle { model().restoreLayout(before) }
        }
    }
}
