package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The Media player, Glance and Liquid battery widgets, placed on Home and drawn. */
class NewWidgetsIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun present(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private var layoutBefore: HomeLayout? = null

    /** Puts Home back as it was, so the tests that run after this one still find their apps and widgets. */
    @org.junit.After fun restoreHome() {
        layoutBefore?.let { saved -> compose.runOnIdle { model().restoreLayout(saved) } }
    }

    private fun placeOnClearHome(vararg ids: Int): Int {
        compose.waitUntil(90_000) { !model().state.value.loading }
        layoutBefore = model().state.value.layout
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        compose.runOnIdle {
            model().state.value.order.toList().forEach { model().setPinned(it, false) }
            model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
        }
        compose.waitForIdle()
        val base = (model().state.value.layout.widgetPlacements.maxOfOrNull { it.slot } ?: -1) + 1
        ids.forEachIndexed { i, id ->
            val layout = model().state.value.layout
            val index = (0 until HOME_CELLS).first { widgetCandidate(layout, base + i, it, 2, 2) != null }
            compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, base + i, index, 2, 2)!!.copy(id = id))) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
        return base
    }

    @Test fun glanceAndLiquidBatteryDraw() {
        val base = placeOnClearHome(UnoWidgets.GLANCE, UnoWidgets.LIQUID_BATTERY)
        compose.waitUntil(10_000) { present("glance-time") && present("liquid-battery-canvas-${base + 1}") }
        assertTrue(present("liquid-battery-percent"))
    }

    @Test fun mediaPlayerSaysWhatToTurnOnThenShowsTheTrackWithASeekBar() {
        val session = MediaSession(compose.activity, "uno-test")
        try {
            val base = placeOnClearHome(UnoWidgets.MEDIA_PLAYER)
            compose.runOnIdle { NotificationFeed.nowPlaying = null }
            compose.waitUntil(10_000) { present("media-empty-hint") }

            session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Test Track")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Test Artist").putLong(MediaMetadata.METADATA_KEY_DURATION, 200_000L).build())
            session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 50_000L, 1f).build())
            session.isActive = true
            val controller = MediaController(compose.activity, session.sessionToken)
            compose.runOnIdle {
                NotificationFeed.nowPlaying = NowPlaying("Test Track", "Test Artist", null, true, controller, PlaybackState.STATE_PLAYING,
                    50_000L, 200_000L, SystemClock.elapsedRealtime(), 1f)
            }
            compose.waitUntil(10_000) { present("media-title") && present("media-seek") }
            assertFalse(present("media-empty-hint"))
            // The seek bar is 48 dp tall, so a thumb can hit it.
            val bounds = compose.onAllNodesWithTag("media-seek", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
            assertTrue("seek bar is at least 48 dp tall", bounds.height / compose.activity.resources.displayMetrics.density >= 47.5f)
        } finally {
            compose.runOnIdle { NotificationFeed.nowPlaying = null }
            session.release()
        }
    }
}
