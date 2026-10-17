package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** A double tap on empty Home space locks the screen, only when the person has switched that on, and a single tap does nothing. */
class DoubleTapLockInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    @After fun off() { compose.runOnUiThread { compose.activity.extrasStore.setDoubleTapLock(false) } }

    private fun emptyCell(): String {
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(60_000) { !model.state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        val layout = model.state.value.layout
        val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
        return "home-cell-" + (0 until HOME_CELLS).first { layout.slotAt(it) == null && it !in blocked }
    }

    @Test fun aDoubleTapOnEmptySpaceAsksToLockOnlyWhenSwitchedOn() {
        val cell = emptyCell()
        val before = LockRequests.count
        compose.onNodeWithTag(cell).performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals("off by default", before, LockRequests.count)
        compose.runOnUiThread { compose.activity.extrasStore.setDoubleTapLock(true) }
        compose.waitForIdle()
        compose.onNodeWithTag(cell).performTouchInput { click() }
        compose.waitForIdle(); Thread.sleep(600)
        assertEquals("a single tap does nothing", before, LockRequests.count)
        compose.onNodeWithTag(cell).performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertEquals("a double tap asks to lock", before + 1, LockRequests.count)
    }

    @Test fun withoutTheServiceTheLockReportsItCannotAndDoesNotCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = SystemShadeAccessibilityService.lock(context)
        assertEquals(ShadeOpenResult.SERVICE_DISABLED, result)
    }
}
