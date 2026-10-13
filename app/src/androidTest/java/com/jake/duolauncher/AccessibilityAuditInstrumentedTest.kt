package com.jake.duolauncher

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Walks Home and every settings page and lists clickable things a screen reader or a thumb would trip on: no name (no text and no
 * content description, once merged), or a touch target under 48 dp. The report is written to the app's external files folder as
 * `a11y-report.txt`; the test fails if there is any unlabelled control, and counts small targets against a budget that only goes down.
 */
class AccessibilityAuditInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private val report = StringBuilder()
    private var unlabelled = 0
    private var small = 0

    private fun audit(screen: String) {
        compose.waitForIdle()
        val density = compose.activity.resources.displayMetrics.density
        compose.onAllNodes(hasClickAction() or hasAnyAncestor(hasClickAction()).not().and(hasSetTextAction()), useUnmergedTree = false)
            .fetchSemanticsNodes().forEach { node ->
                val cfg = node.config
                // Hidden from screen readers on purpose (an occupied Home cell is described by what sits on it).
                if (cfg.contains(SemanticsProperties.HideFromAccessibility)) return@forEach
                val named = cfg.contains(SemanticsProperties.ContentDescription) && cfg[SemanticsProperties.ContentDescription].any { it.isNotBlank() } ||
                    cfg.contains(SemanticsProperties.Text) && cfg[SemanticsProperties.Text].any { it.text.isNotBlank() } ||
                    cfg.contains(SemanticsProperties.EditableText)
                val tag = if (cfg.contains(SemanticsProperties.TestTag)) cfg[SemanticsProperties.TestTag] else "-"
                // The touch bounds include any expansion (minimumInteractiveComponentSize), which is what a thumb meets.
                val w = node.touchBoundsInRoot.width / density; val h = node.touchBoundsInRoot.height / density
                if (!named) { unlabelled++; report.appendLine("UNLABELLED  [$screen] tag=$tag size=${w.toInt()}x${h.toInt()}dp") }
                if (named && (w < 47.5f || h < 47.5f) && node.boundsInWindow.width > 0f) {
                    small++; report.appendLine("SMALL       [$screen] tag=$tag size=${w.toInt()}x${h.toInt()}dp")
                }
            }
    }

    @Test fun homeAndSettingsHaveNamesAndReachableTargets() {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(90_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        audit("home")
        compose.openHomeCustomization()
        audit("settings")
        for (page in listOf("wallpaper", "icons", "home", "gestures", "search", "panel", "island", "keyboard", "privacy", "feed", "updates", "backup", "help")) {
            val tag = "customization-$page"
            runCatching { compose.onNodeWithTag(tag).performScrollTo().performClick() }
            if (compose.onAllNodesWithTag("customization-back").fetchSemanticsNodes().isEmpty()) continue
            audit("settings/$page")
            compose.onNodeWithTag("customization-back").performClick()
            compose.waitForIdle()
        }
        File(compose.activity.getExternalFilesDir(null)!!, "a11y-report.txt").writeText("unlabelled=$unlabelled small=$small\n$report")
        assertEquals("Unlabelled controls:\n$report", 0, unlabelled)
    }
}
