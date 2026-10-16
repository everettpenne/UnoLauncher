package com.jake.duolauncher

import com.jake.duolauncher.island.*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pictures of the island as a Pixel 10a shows it (1080 x 2400 with a small round camera at the top centre), taken in real time after the
 * morph has settled, for looking at. They are written to the app's files directory as island-<state>.png; the test asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
class IslandShotsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val state = IslandState()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var session: MediaSession? = null

    @After fun reset() {
        NotificationFeed.liveUpdates = emptyList(); NotificationFeed.ongoingCall = null; NotificationFeed.nowPlaying = null
        IslandTools.toolsOpen = false; session?.release()
    }

    private fun show() {
        compose.setContent {
            DuoTheme(false) {
                DynamicIsland(state, glass = null, deviceStatus = DeviceStatus(battery = 76, charging = true), feedHeadline = "Dune: Part Three trailer lands with the first look at the sandworm riding",
                    environmentOverride = IslandEnvironment(PxRect(515f, 40f, 565f, 90f), 1080f, 80f, screenHeight = 2400f),
                    onSearch = {}, onOpenFeed = {}, onCustomize = {})
            }
        }
        Thread.sleep(2500); compose.waitForIdle()
    }

    /** Opens the island with the clock held still (the island tucks itself away after a few seconds, and waiting would run that out), lets it finish growing. */
    private fun openShot(name: String, open: () -> Unit = { state.toggle() }) {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open() }
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(1_200)
        takeShot(name)
        compose.mainClock.autoAdvance = true
    }

    private fun shot(name: String) {
        Thread.sleep(2200); compose.waitForIdle()
        takeShot(name)
    }

    private fun takeShot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val crop = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, minOf(760, bitmap.height))
        File(context.filesDir, "island-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun states() {
        show()
        shot("1-idle")
        openShot("2-open")
        compose.runOnIdle { state.collapse() }
        val art = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(0xFFE2582B.toInt()) }
        val s = MediaSession(context, "uno-island-shot").also { session = it }
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Night Drive").putString(MediaMetadata.METADATA_KEY_ARTIST, "The Examples")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 240_000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 83_000, 1f).setActions(PlaybackState.ACTION_PLAY_PAUSE).build())
        s.isActive = true
        compose.runOnIdle { NotificationFeed.nowPlaying = NowPlaying("Night Drive", "The Examples", art, true, s.controller); state.setPlaying(System.currentTimeMillis(), true) }
        shot("3-music-pill")
        openShot("4-music-open")
        compose.runOnIdle { state.collapse() }
        compose.runOnIdle { NotificationFeed.nowPlaying = null; state.endPlayback(); state.setPlaying(System.currentTimeMillis(), false) }
        compose.runOnIdle { IslandTools.toolsOpen = true; state.expanded = true }
        shot("5-tools")
        compose.runOnIdle { state.collapse() }
        compose.runOnIdle { state.showPeekCard(PeekNotification("com.example.chat", "Messages", "Ada Lovelace", "Are we still on for lunch? I found the place with the good noodles.", null, null, 6)) }
        shot("6-card")
    }
}
