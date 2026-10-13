package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A developer aid: places the Media player and battery widgets over the active wallpaper and photographs Home (not part of the suites). */
class WidgetShowcaseInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(1_500)
        val b = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null)!!, "show-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun art(): Bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 300f, 300f, intArrayOf(0xFFFF5E3A.toInt(), 0xFFB81D6B.toInt(), 0xFF2A1B6B.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 300f, 300f, p)
        p.shader = null; p.color = 0x66FFFFFF; c.drawCircle(210f, 90f, 70f, p); p.color = 0x33000000; c.drawCircle(80f, 230f, 110f, p)
    }

    @Test fun showcase() {
        compose.waitUntil(90_000) { !model().state.value.loading }
        runCatching { compose.onNodeWithText("Not now").performClick(); compose.waitForIdle() }
        val layoutBefore = model().state.value.layout
        val session = MediaSession(compose.activity, "uno-showcase")
        try {
            compose.runOnIdle {
                model().state.value.order.toList().forEach { model().setPinned(it, false) }
                model().state.value.layout.widgetPlacements.map { it.slot }.forEach { model().removePlacement(DropTarget.Widget(it)) }
            }
            compose.waitForIdle()
            val wanted = listOf(UnoWidgets.MEDIA_PLAYER to (2 to 2), UnoWidgets.LIQUID_BATTERY to (2 to 2), UnoWidgets.MEDIA_PLAYER to (4 to 2), UnoWidgets.BATTERY to (2 to 2))
            wanted.forEachIndexed { i, (id, span) ->
                val layout = model().state.value.layout
                val index = (0 until HOME_CELLS).first { widgetCandidate(layout, i, it, span.first, span.second) != null }
                compose.runOnIdle { assertTrue(model().placeWidget(widgetCandidate(layout, i, index, span.first, span.second)!!.copy(id = id))) }
            }
            session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Midnight Dunes")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "The Glass Orchestra").putLong(MediaMetadata.METADATA_KEY_DURATION, 214_000L).build())
            session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 83_000L, 1f).build())
            session.isActive = true
            val controller = MediaController(compose.activity, session.sessionToken)
            compose.runOnIdle {
                NotificationFeed.nowPlaying = NowPlaying("Midnight Dunes", "The Glass Orchestra", art(), true, controller, PlaybackState.STATE_PLAYING,
                    83_000L, 214_000L, SystemClock.elapsedRealtime(), 1f)
            }
            compose.onNodeWithContentDescription("Home page 1").performClick()
            shot(InstrumentationRegistry.getArguments().getString("name") ?: "home")
        } finally {
            compose.runOnIdle { NotificationFeed.nowPlaying = null; model().restoreLayout(layoutBefore) }
            session.release()
        }
    }
}
